package com.zeromail.api.controllers.thread;

import com.zeromail.api.dto.thread.NeedsReplyListResponse;
import com.zeromail.api.dto.thread.ToReplyCountResponse;
import com.zeromail.api.error.InvalidCursorException;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailThreadDisplay;
import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.projection.NeedsReplyInboxQueryService;
import com.zeromail.core.thread.projection.NeedsReplyPage;
import com.zeromail.core.thread.projection.NeedsReplyPageQuery;
import com.zeromail.core.thread.projection.NeedsReplyRow;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
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

    public NeedsReplyInboxController(
            NeedsReplyInboxQueryService needsReplyInboxQueryService,
            GmailPreviewReadService gmailPreviewReadService) {
        this.needsReplyInboxQueryService = needsReplyInboxQueryService;
        this.gmailPreviewReadService = gmailPreviewReadService;
    }

    @GetMapping
    public NeedsReplyListResponse list(
            @RequestParam String bucket,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false, defaultValue = "false") boolean resolved) {
        UUID tenantId = currentTenantId();
        validateCursor(cursor);
        ThreadReplyBucket replyBucket = resolved ? null : publicBucket(bucket);
        NeedsReplyPage page =
                needsReplyInboxQueryService.page(
                        tenantId, new NeedsReplyPageQuery(replyBucket, resolved, limit, cursor));
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
        UUID tenantId = currentTenantId();
        return ToReplyCountResponse.from(needsReplyInboxQueryService.toReplyCount(tenantId));
    }

    private static void validateCursor(String cursor) {
        try {
            KeysetCursor.decode(cursor);
        } catch (IllegalArgumentException invalidCursor) {
            throw new InvalidCursorException(invalidCursor);
        }
    }

    private static ThreadReplyBucket publicBucket(String bucket) {
        try {
            return ThreadReplyBucket.fromPublicSlug(bucket.trim().toLowerCase(Locale.ROOT));
        } catch (NoSuchElementException unknownBucket) {
            throw new IllegalArgumentException("unknown thread bucket", unknownBucket);
        }
    }

    private static UUID currentTenantId() {
        return UUID.fromString(TenantContext.currentOrThrow());
    }
}
