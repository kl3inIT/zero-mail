package com.zeromail.worker.inbox;

import com.zeromail.core.gmail.usecases.InboxBackfillService;
import com.zeromail.core.mailbox.MailboxRef;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Worker-side handler for {@code INBOX_PROJECTION_BACKFILL} processing_job rows. Delegates to
 * {@link InboxBackfillService} which does the Gmail fetch + bulk upsert under the tenant scope
 * already bound by {@code ProcessingJobWorker}.
 */
@Component
public class InboxBackfillJobHandler {

    private final InboxBackfillService inboxBackfillService;
    private final ObjectMapper objectMapper;

    public InboxBackfillJobHandler(
            InboxBackfillService inboxBackfillService, ObjectMapper objectMapper) {
        this.inboxBackfillService =
                Objects.requireNonNull(
                        inboxBackfillService, "inboxBackfillService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public void handle(UUID jobId, UUID tenantId, String payload) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        BackfillPayload backfillPayload = parsePayload(payload);
        inboxBackfillService.backfillMailbox(
                new MailboxRef(tenantId, backfillPayload.gmailConnectionId()));
    }

    private BackfillPayload parsePayload(String payloadJson) {
        try {
            BackfillPayload backfillPayload =
                    objectMapper.readValue(payloadJson, BackfillPayload.class);
            if (backfillPayload.gmailConnectionId() == null) {
                throw new IllegalStateException(
                        "Inbox backfill payload is missing gmailConnectionId");
            }
            return backfillPayload;
        } catch (JacksonException malformedPayload) {
            throw new IllegalStateException("Malformed inbox backfill payload", malformedPayload);
        }
    }

    private record BackfillPayload(UUID gmailConnectionId) {}
}
