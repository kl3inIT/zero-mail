package com.zeromail.core.triage.usecases;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListDraftsResponse;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.inbox.usecases.InboxProjectionWriteService;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.exception.MissingMessageIdException;
import com.zeromail.core.triage.exception.ThreadingHeaderInvalidException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only triage class allowed to call Gmail write APIs.
 *
 * <p>{@code TriageGmailWriteBoundaryTest} and {@code NoGmailSendAllowedTest} enforce this boundary.
 * The orchestrator relies on {@code users.messages.modify} idempotency for label/archive retries;
 * {@code users.drafts.create} is intentionally guarded by the audit PENDING-to-APPLIED loop because
 * Gmail draft creation is not idempotent.
 */
@Component
public class TriageGmailWriter {

    private static final Logger log = LoggerFactory.getLogger(TriageGmailWriter.class);
    private static final String USER_ID = "me";
    private static final String INBOX_LABEL_ID = "INBOX";
    private static final String UNREAD_LABEL_ID = "UNREAD";
    private static final String STARRED_LABEL_ID = "STARRED";
    private static final String SPAM_LABEL_ID = "SPAM";
    private static final String DIGEST_LABEL_NAME = "Zero Mail/Digest";

    private final GmailApiClientFactory gmailApiClientFactory;
    private final InboxProjectionWriteService inboxProjectionWriteService;

    public TriageGmailWriter(
            GmailApiClientFactory gmailApiClientFactory,
            InboxProjectionWriteService inboxProjectionWriteService) {
        this.gmailApiClientFactory =
                Objects.requireNonNull(
                        gmailApiClientFactory, "gmailApiClientFactory must not be null");
        this.inboxProjectionWriteService =
                Objects.requireNonNull(
                        inboxProjectionWriteService,
                        "inboxProjectionWriteService must not be null");
    }

    public String applyLabel(UUID tenantId, String gmailMessageId, String labelName)
            throws IOException {
        String resolvedLabelId =
                executeGmailWrite(
                        tenantId,
                        "applyLabel",
                        gmail -> {
                            String labelId = resolveOrCreateLabelId(gmail, labelName);
                            gmail.users()
                                    .messages()
                                    .modify(
                                            USER_ID,
                                            gmailMessageId,
                                            new ModifyMessageRequest()
                                                    .setAddLabelIds(List.of(labelId)))
                                    .execute();
                            logMessageWrite(tenantId, gmailMessageId, "applyLabel");
                            return labelId;
                        });
        // Gmail confirmed first; mirror the resolved label id (e.g. "Label_42") into the projection
        // so the DB-backed inbox list shows the AI label immediately instead of waiting for the
        // next
        // Pub/Sub reconcile. The read path maps the id back to the display name via labels.list.
        inboxProjectionWriteService.addLabel(tenantId, gmailMessageId, resolvedLabelId);
        return resolvedLabelId;
    }

    public void archiveSkipInbox(UUID tenantId, String gmailMessageId) throws IOException {
        executeGmailWrite(
                tenantId,
                "archiveSkipInbox",
                gmail -> {
                    gmail.users()
                            .messages()
                            .modify(
                                    USER_ID,
                                    gmailMessageId,
                                    new ModifyMessageRequest()
                                            .setRemoveLabelIds(List.of(INBOX_LABEL_ID)))
                            .execute();
                    logMessageWrite(tenantId, gmailMessageId, "archiveSkipInbox");
                    return null;
                });
        inboxProjectionWriteService.removeLabel(tenantId, gmailMessageId, INBOX_LABEL_ID);
    }

    /**
     * Drop the Gmail {@code UNREAD} system label AND mirror the change into the inbox projection so
     * the next DB-backed read returns the same state the optimistic UI already shows (Phase B Wave
     * 2). Gmail call happens first; the projection write runs only after Gmail confirms — if Gmail
     * throws, the projection stays untouched and the next Pub/Sub event will reconcile.
     */
    public void markRead(UUID tenantId, String gmailMessageId) throws IOException {
        removeSystemLabel(tenantId, gmailMessageId, UNREAD_LABEL_ID, "markRead");
        inboxProjectionWriteService.markRead(tenantId, gmailMessageId);
    }

    public void markUnread(UUID tenantId, String gmailMessageId) throws IOException {
        addSystemLabel(tenantId, gmailMessageId, UNREAD_LABEL_ID, "markUnread");
        inboxProjectionWriteService.addLabel(tenantId, gmailMessageId, UNREAD_LABEL_ID);
    }

    public void star(UUID tenantId, String gmailMessageId) throws IOException {
        addSystemLabel(tenantId, gmailMessageId, STARRED_LABEL_ID, "star");
        inboxProjectionWriteService.addLabel(tenantId, gmailMessageId, STARRED_LABEL_ID);
    }

    public void unstar(UUID tenantId, String gmailMessageId) throws IOException {
        removeSystemLabel(tenantId, gmailMessageId, STARRED_LABEL_ID, "unstar");
        inboxProjectionWriteService.removeLabel(tenantId, gmailMessageId, STARRED_LABEL_ID);
    }

    public String addToDigest(UUID tenantId, String gmailMessageId) throws IOException {
        return applyLabel(tenantId, gmailMessageId, DIGEST_LABEL_NAME);
    }

    public void markSpam(UUID tenantId, String gmailMessageId) throws IOException {
        executeGmailWrite(
                tenantId,
                "markSpam",
                gmail -> {
                    gmail.users()
                            .messages()
                            .modify(
                                    USER_ID,
                                    gmailMessageId,
                                    new ModifyMessageRequest()
                                            .setAddLabelIds(List.of(SPAM_LABEL_ID))
                                            .setRemoveLabelIds(List.of(INBOX_LABEL_ID)))
                            .execute();
                    logMessageWrite(tenantId, gmailMessageId, "markSpam");
                    return null;
                });
        inboxProjectionWriteService.removeLabel(tenantId, gmailMessageId, INBOX_LABEL_ID);
        inboxProjectionWriteService.addLabel(tenantId, gmailMessageId, SPAM_LABEL_ID);
    }

    public void unmarkSpam(UUID tenantId, String gmailMessageId) throws IOException {
        executeGmailWrite(
                tenantId,
                "unmarkSpam",
                gmail -> {
                    gmail.users()
                            .messages()
                            .modify(
                                    USER_ID,
                                    gmailMessageId,
                                    new ModifyMessageRequest()
                                            .setAddLabelIds(List.of(INBOX_LABEL_ID))
                                            .setRemoveLabelIds(List.of(SPAM_LABEL_ID)))
                            .execute();
                    logMessageWrite(tenantId, gmailMessageId, "unmarkSpam");
                    return null;
                });
        inboxProjectionWriteService.addLabel(tenantId, gmailMessageId, INBOX_LABEL_ID);
        inboxProjectionWriteService.removeLabel(tenantId, gmailMessageId, SPAM_LABEL_ID);
    }

    public String saveDraft(
            UUID tenantId, ReplyHeaders replyHeaders, String body, String gmailThreadId)
            throws IOException {
        Objects.requireNonNull(replyHeaders, "replyHeaders must not be null");
        requireText(body, "body");
        requireText(gmailThreadId, "gmailThreadId");

        Message draftMessage;
        try {
            String encodedMimeMessage = ReplyMimeBuilder.buildBase64UrlMime(replyHeaders, body);
            MimeMessage mimeMessage = ReplyMimeBuilder.parseBase64UrlMime(encodedMimeMessage);
            draftMessage =
                    new Message()
                            .setThreadId(replyHeaders.gmailThreadId())
                            .setRaw(encodedMimeMessage);
            ThreadingHeaderValidator.validate(mimeMessage, draftMessage, gmailThreadId);
        } catch (MissingMessageIdException | ThreadingHeaderInvalidException threadingException) {
            log.warn(
                    "event=draft_threading_invalid tenantId={} gmailThreadId={}",
                    tenantId,
                    gmailThreadId);
            throw threadingException;
        } catch (MessagingException messagingException) {
            throw new IOException("Unable to build reply MIME", messagingException);
        }

        return executeGmailWrite(
                tenantId,
                "saveDraft",
                gmail -> {
                    Draft createdDraft =
                            gmail.users()
                                    .drafts()
                                    .create(USER_ID, new Draft().setMessage(draftMessage))
                                    .execute();
                    logThreadWrite(tenantId, gmailThreadId);
                    return createdDraft.getId();
                });
    }

    public String saveDraftMessage(UUID tenantId, Message draftMessage, String gmailThreadId)
            throws IOException {
        Objects.requireNonNull(draftMessage, "draftMessage must not be null");
        return executeGmailWrite(
                tenantId,
                "saveDraftMessage",
                gmail -> {
                    Draft createdDraft =
                            gmail.users()
                                    .drafts()
                                    .create(USER_ID, new Draft().setMessage(draftMessage))
                                    .execute();
                    logThreadWrite(tenantId, gmailThreadId);
                    return createdDraft.getId();
                });
    }

    public void removeLabel(UUID tenantId, String gmailMessageId, String labelId)
            throws IOException {
        executeGmailWrite(
                tenantId,
                "removeLabel",
                gmail -> {
                    gmail.users()
                            .messages()
                            .modify(
                                    USER_ID,
                                    gmailMessageId,
                                    new ModifyMessageRequest().setRemoveLabelIds(List.of(labelId)))
                            .execute();
                    logMessageWrite(tenantId, gmailMessageId, "removeLabel");
                    return null;
                });
        inboxProjectionWriteService.removeLabel(tenantId, gmailMessageId, labelId);
    }

    public void restoreToInbox(UUID tenantId, String gmailMessageId) throws IOException {
        executeGmailWrite(
                tenantId,
                "restoreToInbox",
                gmail -> {
                    gmail.users()
                            .messages()
                            .modify(
                                    USER_ID,
                                    gmailMessageId,
                                    new ModifyMessageRequest()
                                            .setAddLabelIds(List.of(INBOX_LABEL_ID)))
                            .execute();
                    logMessageWrite(tenantId, gmailMessageId, "restoreToInbox");
                    return null;
                });
        inboxProjectionWriteService.addLabel(tenantId, gmailMessageId, INBOX_LABEL_ID);
    }

    public void moveToTrash(UUID tenantId, String gmailMessageId) throws IOException {
        executeGmailWrite(
                tenantId,
                "moveToTrash",
                gmail -> {
                    gmail.users().messages().trash(USER_ID, gmailMessageId).execute();
                    logMessageWrite(tenantId, gmailMessageId, "moveToTrash");
                    return null;
                });
    }

    /**
     * H-2 — Look up the Gmail-side label id for a known label name. Returns {@link
     * Optional#empty()} when the label does not exist (e.g. user manually deleted the {@code "Zero
     * Mail/Unsubscribed"} label between campaign apply + undo). Used by {@code CampaignUndoService}
     * (Phase 8 Plan 07) to skip the {@code removeLabel} step gracefully instead of throwing.
     *
     * <p>Propagates {@link IOException} on Gmail API failure — matches the {@link #applyLabel}
     * error-propagation convention so the caller's retry semantics are consistent.
     */
    public Optional<String> lookupLabelId(UUID tenantId, String labelName) throws IOException {
        if (tenantId == null) {
            throw new IOException("tenantId must not be null");
        }
        requireText(labelName, "labelName");
        Gmail gmail = gmailApiClientFactory.buildClientForTenant(tenantId);
        return findLabelIdByName(gmail, labelName);
    }

    /**
     * H-2 — Resolve-or-create the Gmail-side label id for a known label name. Returns the opaque
     * Gmail label id (e.g. {@code "Label_42"}) as a {@link String}. Idempotent: re-invoking with
     * the same label name returns the same id without creating a duplicate Gmail label.
     *
     * <p>Used by {@code UnsubscribeCampaignHandler} (Phase 8 Plan 06) to capture the label id once
     * per campaign so each archived message records the same id via {@code
     * TriageAuditWriter.recordCleanupArchive}.
     */
    public String ensureLabelExists(UUID tenantId, String labelName) throws IOException {
        if (tenantId == null) {
            throw new IOException("tenantId must not be null");
        }
        requireText(labelName, "labelName");
        return executeGmailWrite(
                tenantId, "ensureLabelExists", gmail -> resolveOrCreateLabelId(gmail, labelName));
    }

    /**
     * Update the MIME content of an existing Gmail draft. Used by the composer auto-save loop: when
     * the user types into the inbox reply composer, the FE debounces and calls the composer draft
     * service, which routes through here to keep the Gmail draft in sync. Idempotent: if Gmail
     * returns 404 the caller is expected to retry as create.
     *
     * <p>Mirrors {@link #saveDraftMessage} but routes through {@code drafts().update} so the same
     * draftId is preserved across composer keystrokes (one draft per thread, not many).
     */
    public String updateDraftMessage(
            UUID tenantId, String draftId, Message draftMessage, String gmailThreadId)
            throws IOException {
        Objects.requireNonNull(draftMessage, "draftMessage must not be null");
        return executeGmailWrite(
                tenantId,
                "updateDraftMessage",
                gmail -> {
                    Draft updatedDraft =
                            gmail.users()
                                    .drafts()
                                    .update(USER_ID, draftId, new Draft().setMessage(draftMessage))
                                    .execute();
                    logThreadWrite(tenantId, gmailThreadId);
                    return updatedDraft.getId();
                });
    }

    /**
     * Look up every Gmail draft that belongs to the given thread. Returns the raw Gmail draftIds so
     * callers can choose which one to load. Drafts.list is paginated; we cap at {@code maxDrafts}
     * to keep latency bounded for users with massive draft folders. For composer caching we
     * typically only care about the most recent draft on a specific thread.
     */
    public List<String> listDraftIdsForThread(UUID tenantId, String gmailThreadId, int maxDrafts)
            throws IOException {
        requireText(gmailThreadId, "gmailThreadId");
        return executeGmailWrite(
                tenantId,
                "listDraftIdsForThread",
                gmail -> {
                    List<String> matchingDraftIds = new ArrayList<>();
                    String nextPageToken = null;
                    int scannedDrafts = 0;
                    do {
                        ListDraftsResponse listResponse =
                                gmail.users()
                                        .drafts()
                                        .list(USER_ID)
                                        .setMaxResults((long) Math.min(50, maxDrafts))
                                        .setPageToken(nextPageToken)
                                        .execute();
                        List<Draft> pageDrafts =
                                listResponse.getDrafts() == null
                                        ? List.of()
                                        : listResponse.getDrafts();
                        for (Draft scannedDraft : pageDrafts) {
                            scannedDrafts++;
                            Message draftMessage = scannedDraft.getMessage();
                            if (draftMessage != null
                                    && gmailThreadId.equals(draftMessage.getThreadId())
                                    && hasText(scannedDraft.getId())) {
                                matchingDraftIds.add(scannedDraft.getId());
                            }
                            if (scannedDrafts >= maxDrafts) {
                                break;
                            }
                        }
                        nextPageToken = listResponse.getNextPageToken();
                    } while (hasText(nextPageToken) && scannedDrafts < maxDrafts);
                    return List.copyOf(matchingDraftIds);
                });
    }

    /**
     * Fetch a single draft in {@code RAW} format so callers can parse the underlying MIME headers
     * and body. The composer draft service uses this to restore To/Cc/Bcc/Subject/Body when the
     * user reopens the composer for a thread that already has a draft.
     */
    public Optional<Draft> fetchDraftRaw(UUID tenantId, String draftId) throws IOException {
        requireText(draftId, "draftId");
        return executeGmailWrite(
                tenantId,
                "fetchDraftRaw",
                gmail -> {
                    try {
                        return Optional.of(
                                gmail.users()
                                        .drafts()
                                        .get(USER_ID, draftId)
                                        .setFormat("RAW")
                                        .execute());
                    } catch (GoogleJsonResponseException googleResponseException) {
                        if (googleResponseException.getStatusCode() == 404) {
                            return Optional.<Draft>empty();
                        }
                        throw googleResponseException;
                    }
                });
    }

    public void deleteDraft(UUID tenantId, String draftId) throws IOException {
        executeGmailWrite(
                tenantId,
                "deleteDraft",
                gmail -> {
                    try {
                        gmail.users().drafts().delete(USER_ID, draftId).execute();
                    } catch (GoogleJsonResponseException googleResponseException) {
                        if (googleResponseException.getStatusCode() == 404) {
                            log.info(
                                    "event=triage_gmail_write_idempotent_skip tenantId={} draftId={} op={}",
                                    tenantId,
                                    draftId,
                                    "deleteDraft");
                            return null;
                        }
                        throw googleResponseException;
                    }
                    log.info(
                            "event=triage_gmail_write tenantId={} draftId={} op={}",
                            tenantId,
                            draftId,
                            "deleteDraft");
                    return null;
                });
    }

    private void addSystemLabel(
            UUID tenantId, String gmailMessageId, String labelId, String operation)
            throws IOException {
        executeGmailWrite(
                tenantId,
                operation,
                gmail -> {
                    gmail.users()
                            .messages()
                            .modify(
                                    USER_ID,
                                    gmailMessageId,
                                    new ModifyMessageRequest().setAddLabelIds(List.of(labelId)))
                            .execute();
                    logMessageWrite(tenantId, gmailMessageId, operation);
                    return null;
                });
    }

    private void removeSystemLabel(
            UUID tenantId, String gmailMessageId, String labelId, String operation)
            throws IOException {
        executeGmailWrite(
                tenantId,
                operation,
                gmail -> {
                    gmail.users()
                            .messages()
                            .modify(
                                    USER_ID,
                                    gmailMessageId,
                                    new ModifyMessageRequest().setRemoveLabelIds(List.of(labelId)))
                            .execute();
                    logMessageWrite(tenantId, gmailMessageId, operation);
                    return null;
                });
    }

    private <T> T executeGmailWrite(
            UUID tenantId, String operation, GmailWriteOperation<T> gmailWriteOperation)
            throws IOException {
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(tenantId);
            return gmailWriteOperation.execute(gmail);
        } catch (GoogleJsonResponseException googleResponseException) {
            log.warn(
                    "event=triage_gmail_write_failed tenantId={} op={} status={}",
                    tenantId,
                    operation,
                    googleResponseException.getStatusCode());
            throw googleResponseException;
        } catch (IOException ioException) {
            log.warn("event=triage_gmail_write_failed tenantId={} op={}", tenantId, operation);
            throw ioException;
        }
    }

    private static String resolveOrCreateLabelId(Gmail gmail, String labelName) throws IOException {
        requireText(labelName, "labelName");
        if (isLikelyGmailLabelId(labelName)) {
            return labelName;
        }
        Optional<String> existingLabelId = findLabelIdByName(gmail, labelName);
        if (existingLabelId.isPresent()) {
            return existingLabelId.get();
        }
        try {
            Label createdLabel =
                    gmail.users()
                            .labels()
                            .create(
                                    USER_ID,
                                    new Label()
                                            .setName(labelName)
                                            .setLabelListVisibility("labelShow")
                                            .setMessageListVisibility("show"))
                            .execute();
            return requireText(createdLabel.getId(), "createdLabelId");
        } catch (GoogleJsonResponseException googleResponseException) {
            if (googleResponseException.getStatusCode() == 409) {
                return findLabelIdByName(gmail, labelName)
                        .orElseThrow(() -> googleResponseException);
            }
            throw googleResponseException;
        }
    }

    private static Optional<String> findLabelIdByName(Gmail gmail, String labelName)
            throws IOException {
        ListLabelsResponse labelsResponse = gmail.users().labels().list(USER_ID).execute();
        List<Label> gmailLabels = labelsResponse.getLabels();
        if (gmailLabels == null) {
            return Optional.empty();
        }
        return gmailLabels.stream()
                .filter(gmailLabel -> labelName.equals(gmailLabel.getName()))
                .map(Label::getId)
                .filter(TriageGmailWriter::hasText)
                .findFirst();
    }

    private static boolean isLikelyGmailLabelId(String labelName) {
        return labelName.startsWith("Label_") || INBOX_LABEL_ID.equals(labelName);
    }

    private static void logMessageWrite(UUID tenantId, String gmailMessageId, String operation) {
        log.info(
                "event=triage_gmail_write tenantId={} gmailMessageId={} op={}",
                tenantId,
                stripCrlf(gmailMessageId),
                operation);
    }

    private static void logThreadWrite(UUID tenantId, String gmailThreadId) {
        log.info(
                "event=triage_gmail_write tenantId={} gmailThreadId={} op={}",
                tenantId,
                stripCrlf(gmailThreadId),
                "saveDraft");
    }

    private static String stripCrlf(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", "_");
    }

    private static String requireText(String text, String fieldName) throws IOException {
        if (!hasText(text)) {
            throw new IOException(fieldName + " must not be blank");
        }
        return text;
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    @FunctionalInterface
    private interface GmailWriteOperation<T> {
        T execute(Gmail gmail) throws IOException;
    }
}
