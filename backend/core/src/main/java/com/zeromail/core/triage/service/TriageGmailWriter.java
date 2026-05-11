package com.zeromail.core.triage.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.zeromail.core.gmail.service.GmailApiClientFactory;
import com.zeromail.core.triage.domain.TriageActionResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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

    private final GmailApiClientFactory gmailApiClientFactory;

    public TriageGmailWriter(GmailApiClientFactory gmailApiClientFactory) {
        this.gmailApiClientFactory = gmailApiClientFactory;
    }

    public void applyLabel(UUID tenantId, String gmailMessageId, String labelId)
            throws IOException {
        executeGmailWrite(
                tenantId,
                "applyLabel",
                gmail -> {
                    gmail.users()
                            .messages()
                            .modify(
                                    USER_ID,
                                    gmailMessageId,
                                    new ModifyMessageRequest().setAddLabelIds(List.of(labelId)))
                            .execute();
                    logMessageWrite(tenantId, gmailMessageId, "applyLabel");
                    return null;
                });
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
    }

    public String saveDraft(
            UUID tenantId, TriageActionResult.SaveDraft draftSpec, String gmailThreadId)
            throws IOException {
        return executeGmailWrite(
                tenantId,
                "saveDraft",
                gmail -> {
                    Draft createdDraft =
                            gmail.users()
                                    .drafts()
                                    .create(
                                            USER_ID,
                                            new Draft()
                                                    .setMessage(
                                                            draftMessage(
                                                                    draftSpec.instruction(),
                                                                    gmailThreadId)))
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
    }

    public void deleteDraft(UUID tenantId, String draftId) throws IOException {
        executeGmailWrite(
                tenantId,
                "deleteDraft",
                gmail -> {
                    gmail.users().drafts().delete(USER_ID, draftId).execute();
                    log.info(
                            "event=triage_gmail_write tenantId={} draftId={} op={}",
                            tenantId,
                            draftId,
                            "deleteDraft");
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

    private static Message draftMessage(String instruction, String gmailThreadId) {
        String rawMimeMessage =
                "MIME-Version: 1.0\r\n"
                        + "Content-Type: text/plain; charset=UTF-8\r\n"
                        + "Content-Transfer-Encoding: 8bit\r\n"
                        + "\r\n"
                        + instruction;
        String encodedMimeMessage =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(rawMimeMessage.getBytes(StandardCharsets.UTF_8));
        return new Message().setThreadId(gmailThreadId).setRaw(encodedMimeMessage);
    }

    private static void logMessageWrite(UUID tenantId, String gmailMessageId, String operation) {
        log.info(
                "event=triage_gmail_write tenantId={} gmailMessageId={} op={}",
                tenantId,
                gmailMessageId,
                operation);
    }

    private static void logThreadWrite(UUID tenantId, String gmailThreadId) {
        log.info(
                "event=triage_gmail_write tenantId={} gmailThreadId={} op={}",
                tenantId,
                gmailThreadId,
                "saveDraft");
    }

    @FunctionalInterface
    private interface GmailWriteOperation<T> {
        T execute(Gmail gmail) throws IOException;
    }
}
