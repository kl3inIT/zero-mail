package com.zeromail.api.controllers.thread;

import com.zeromail.api.dto.thread.NeedsReplyBackfillResponse;
import com.zeromail.api.dto.thread.NeedsReplyCountsResponse;
import com.zeromail.api.dto.thread.NeedsReplyListResponse;
import com.zeromail.api.dto.thread.ToReplyCountResponse;
import com.zeromail.api.error.InvalidCursorException;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailThreadDisplay;
import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.projection.NeedsReplyPage;
import com.zeromail.core.thread.projection.NeedsReplyPageQuery;
import com.zeromail.core.thread.projection.NeedsReplyRow;
import com.zeromail.core.thread.usecases.NeedsReplyInboxQueryService;
import com.zeromail.core.triage.usecases.BackfillNeedsReplyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "thread-inbox")
@RequestMapping("/api/threads")
public class NeedsReplyInboxController {

    private static final Duration GMAIL_DISPLAY_FETCH_BUDGET = Duration.ofSeconds(3);

    private final NeedsReplyInboxQueryService needsReplyInboxQueryService;
    private final GmailPreviewReadService gmailPreviewReadService;
    private final BackfillNeedsReplyService backfillNeedsReplyService;

    public NeedsReplyInboxController(
            NeedsReplyInboxQueryService needsReplyInboxQueryService,
            GmailPreviewReadService gmailPreviewReadService,
            BackfillNeedsReplyService backfillNeedsReplyService) {
        this.needsReplyInboxQueryService = needsReplyInboxQueryService;
        this.gmailPreviewReadService = gmailPreviewReadService;
        this.backfillNeedsReplyService = backfillNeedsReplyService;
    }

    @GetMapping
    public NeedsReplyListResponse list(
            @RequestParam String bucket,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean resolved) {
        UUID tenantId = TenantContext.currentTenantUuid();
        validateCursor(cursor);
        BucketSelection bucketSelection =
                resolved ? BucketSelection.resolved() : publicBucket(bucket);
        NeedsReplyPage page =
                needsReplyInboxQueryService.page(
                        tenantId,
                        new NeedsReplyPageQuery(
                                bucketSelection.bucket(),
                                resolved,
                                bucketSelection.draftedOnly(),
                                limit,
                                cursor));
        List<String> gmailThreadIds =
                page.items().stream().map(NeedsReplyRow::gmailThreadId).toList();
        Map<String, GmailThreadDisplay> displaysByThreadId =
                gmailPreviewReadService.fetchThreadDisplays(
                        tenantId, gmailThreadIds, GMAIL_DISPLAY_FETCH_BUDGET);
        Long toReplyCount =
                cursor == null || cursor.isBlank()
                        ? needsReplyInboxQueryService.toReplyCount(tenantId)
                        : null;
        return NeedsReplyListResponse.from(page, displaysByThreadId, toReplyCount);
    }

    @GetMapping("/to-reply-count")
    public ToReplyCountResponse toReplyCount() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return ToReplyCountResponse.from(needsReplyInboxQueryService.toReplyCount(tenantId));
    }

    @GetMapping("/counts")
    public NeedsReplyCountsResponse counts() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return NeedsReplyCountsResponse.from(needsReplyInboxQueryService.counts(tenantId));
    }

    @PostMapping("/backfill")
    public NeedsReplyBackfillResponse backfill(@RequestParam(defaultValue = "20") int limit) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return NeedsReplyBackfillResponse.from(backfillNeedsReplyService.backfill(tenantId, limit));
    }

    private static void validateCursor(String cursor) {
        try {
            KeysetCursor.decode(cursor);
        } catch (IllegalArgumentException invalidCursor) {
            throw new InvalidCursorException(invalidCursor);
        }
    }

    private static BucketSelection publicBucket(String bucket) {
        String normalizedBucket = bucket == null ? "" : bucket.trim().toLowerCase(Locale.ROOT);
        if ("drafted".equals(normalizedBucket)) {
            return new BucketSelection(ThreadReplyBucket.TO_REPLY, true);
        }
        try {
            return new BucketSelection(ThreadReplyBucket.fromPublicSlug(normalizedBucket), false);
        } catch (NoSuchElementException unknownBucket) {
            throw new IllegalArgumentException("unknown thread bucket", unknownBucket);
        }
    }

    private record BucketSelection(ThreadReplyBucket bucket, boolean draftedOnly) {
        static BucketSelection resolved() {
            return new BucketSelection(null, false);
        }
    }
}
