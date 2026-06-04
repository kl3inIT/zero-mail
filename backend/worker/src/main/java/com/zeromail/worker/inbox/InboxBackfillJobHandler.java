package com.zeromail.worker.inbox;

import com.zeromail.core.gmail.usecases.InboxBackfillService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Worker-side handler for {@code INBOX_PROJECTION_BACKFILL} processing_job rows. Delegates to
 * {@link InboxBackfillService} which does the Gmail fetch + bulk upsert under the tenant scope
 * already bound by {@code ProcessingJobWorker}.
 */
@Component
public class InboxBackfillJobHandler {

    private final InboxBackfillService inboxBackfillService;

    public InboxBackfillJobHandler(InboxBackfillService inboxBackfillService) {
        this.inboxBackfillService =
                Objects.requireNonNull(
                        inboxBackfillService, "inboxBackfillService must not be null");
    }

    public void handle(UUID jobId, UUID tenantId, String payload) {
        // payload is "{}" — no params required; jobId is forwarded for log correlation by the
        // worker's structured log frame.
        inboxBackfillService.backfillTenant(tenantId);
    }
}
