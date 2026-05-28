package com.zeromail.core.composer.usecases;

import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Composer draft caching against Gmail itself: when the user types into the inbox reply composer,
 * we keep one Gmail draft per (tenant, gmailThreadId) in sync so leaving the page and returning (or
 * opening Gmail on another device) restores the in-progress draft.
 *
 * <p>All Gmail writes go through {@link TriageGmailWriter} so the {@code GmailWriteBoundaryTest}
 * arch contract continues to hold. We deliberately do NOT mirror the draft body into our
 * application database — Gmail is the source of truth, which avoids growing the privacy surface
 * (the draft body itself is user-authored under the CLAUDE.md draft-body carve-out, but Gmail
 * already retains it durably so there is no value in shadow-storing it locally).
 */
@Service
public class ComposerDraftService {

    private static final Logger log = LoggerFactory.getLogger(ComposerDraftService.class);
    private static final int MAX_DRAFTS_TO_SCAN = 200;

    private final TriageGmailWriter triageGmailWriter;

    public ComposerDraftService(TriageGmailWriter triageGmailWriter) {
        this.triageGmailWriter = Objects.requireNonNull(triageGmailWriter, "triageGmailWriter");
    }

    /**
     * Look up the user's draft for a Gmail thread and return its current body / recipients /
     * subject if one exists. Returns {@link Optional#empty()} when no draft is found.
     */
    public Optional<ComposerDraftSnapshot> find(UUID tenantId, String gmailThreadId)
            throws IOException {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
        List<String> matchingDraftIds =
                triageGmailWriter.listDraftIdsForThread(
                        tenantId, gmailThreadId, MAX_DRAFTS_TO_SCAN);
        if (matchingDraftIds.isEmpty()) {
            return Optional.empty();
        }
        if (matchingDraftIds.size() > 1) {
            log.info(
                    "event=composer_multiple_drafts_for_thread tenantId={} gmailThreadId={} count={}",
                    tenantId,
                    redact(gmailThreadId),
                    matchingDraftIds.size());
        }
        for (String candidateDraftId : matchingDraftIds) {
            Optional<ComposerDraftSnapshot> parsed =
                    fetchAndParse(tenantId, candidateDraftId, gmailThreadId);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    /**
     * Create or update the user's draft for a Gmail thread. If a draft already exists we route
     * through {@code drafts.update} so the draftId is stable across keystrokes — Gmail otherwise
     * accumulates a new draftId per create call which would flood the user's drafts folder.
     */
    public ComposerDraftSnapshot upsert(UUID tenantId, ComposerDraftUpsertCommand command)
            throws IOException {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Message draftMessage;
        try {
            String encodedMime = ComposerMimeBuilder.buildBase64UrlMime(command);
            draftMessage = new Message().setThreadId(command.gmailThreadId()).setRaw(encodedMime);
        } catch (MessagingException | IOException buildFailure) {
            throw new IOException("Unable to build composer draft MIME", buildFailure);
        }

        List<String> existingDraftIds =
                triageGmailWriter.listDraftIdsForThread(
                        tenantId, command.gmailThreadId(), MAX_DRAFTS_TO_SCAN);
        String writtenDraftId;
        if (existingDraftIds.isEmpty()) {
            writtenDraftId =
                    triageGmailWriter.saveDraftMessage(
                            tenantId, draftMessage, command.gmailThreadId());
            log.info(
                    "event=composer_draft_created tenantId={} gmailThreadId={}",
                    tenantId,
                    redact(command.gmailThreadId()));
        } else {
            String targetDraftId = existingDraftIds.get(0);
            writtenDraftId =
                    triageGmailWriter.updateDraftMessage(
                            tenantId, targetDraftId, draftMessage, command.gmailThreadId());
            log.info(
                    "event=composer_draft_updated tenantId={} gmailThreadId={}",
                    tenantId,
                    redact(command.gmailThreadId()));
            for (int extraIndex = 1; extraIndex < existingDraftIds.size(); extraIndex++) {
                String staleDraftId = existingDraftIds.get(extraIndex);
                try {
                    triageGmailWriter.deleteDraft(tenantId, staleDraftId);
                } catch (IOException staleDraftCleanupFailure) {
                    log.warn(
                            "event=composer_draft_stale_cleanup_failed tenantId={} draftId={}",
                            tenantId,
                            staleDraftId);
                }
            }
        }
        return new ComposerDraftSnapshot(
                writtenDraftId,
                command.gmailThreadId(),
                splitAddresses(command.toAddresses()),
                splitAddresses(command.ccAddresses()),
                splitAddresses(command.bccAddresses()),
                command.subject(),
                command.body());
    }

    public void delete(UUID tenantId, String draftId) throws IOException {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (draftId == null || draftId.isBlank()) {
            return;
        }
        triageGmailWriter.deleteDraft(tenantId, draftId);
    }

    private Optional<ComposerDraftSnapshot> fetchAndParse(
            UUID tenantId, String draftId, String gmailThreadId) throws IOException {
        Optional<Draft> fetched = triageGmailWriter.fetchDraftRaw(tenantId, draftId);
        if (fetched.isEmpty()) {
            return Optional.empty();
        }
        Draft draft = fetched.get();
        Message draftMessage = draft.getMessage();
        if (draftMessage == null || draftMessage.getRaw() == null) {
            return Optional.empty();
        }
        try {
            ComposerMimeBuilder.ParsedDraft parsed =
                    ComposerMimeBuilder.parseRaw(draftMessage.getRaw());
            return Optional.of(
                    new ComposerDraftSnapshot(
                            draftId,
                            gmailThreadId,
                            parsed.toAddresses(),
                            parsed.ccAddresses(),
                            parsed.bccAddresses(),
                            parsed.subject(),
                            parsed.body()));
        } catch (MessagingException | IOException parseFailure) {
            log.warn("event=composer_draft_parse_failed tenantId={} draftId={}", tenantId, draftId);
            return Optional.empty();
        }
    }

    private static List<String> splitAddresses(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            InternetAddress[] addresses = InternetAddress.parse(raw, false);
            List<String> collected = new ArrayList<>(addresses.length);
            for (InternetAddress address : addresses) {
                if (address != null && address.getAddress() != null) {
                    collected.add(address.toString());
                }
            }
            return List.copyOf(collected);
        } catch (AddressException malformedAddress) {
            return List.of();
        }
    }

    private static String redact(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", "_");
    }
}
