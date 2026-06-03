package com.zeromail.core.thread.usecases;

import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.persistence.ThreadReplyStatusRepository;
import com.zeromail.core.thread.persistence.lowlevel.NeedsReplyInboxReadRepository;
import com.zeromail.core.thread.projection.NeedsReplyCounts;
import com.zeromail.core.thread.projection.NeedsReplyPage;
import com.zeromail.core.thread.projection.NeedsReplyPageQuery;
import com.zeromail.core.thread.projection.NeedsReplyRow;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NeedsReplyInboxQueryService {

    private final NeedsReplyInboxReadRepository needsReplyInboxReadRepository;
    private final ThreadReplyStatusRepository threadReplyStatusRepository;

    public NeedsReplyInboxQueryService(
            NeedsReplyInboxReadRepository needsReplyInboxReadRepository,
            ThreadReplyStatusRepository threadReplyStatusRepository) {
        this.needsReplyInboxReadRepository =
                Objects.requireNonNull(
                        needsReplyInboxReadRepository,
                        "needsReplyInboxReadRepository must not be null");
        this.threadReplyStatusRepository =
                Objects.requireNonNull(
                        threadReplyStatusRepository,
                        "threadReplyStatusRepository must not be null");
    }

    @Transactional(readOnly = true)
    public NeedsReplyPage page(UUID tenantId, NeedsReplyPageQuery query) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        NeedsReplyPageQuery pageQuery = Objects.requireNonNull(query, "query must not be null");
        Optional<KeysetCursor> decodedCursor = KeysetCursor.decode(pageQuery.cursor());
        List<NeedsReplyRow> rows =
                needsReplyInboxReadRepository.findPage(tenantId, pageQuery, decodedCursor);
        boolean hasNextPage = rows.size() > pageQuery.limit();
        List<NeedsReplyRow> pageItems = hasNextPage ? rows.subList(0, pageQuery.limit()) : rows;
        String nextCursor = hasNextPage ? cursorFor(pageItems.getLast()) : null;
        return new NeedsReplyPage(pageItems, nextCursor);
    }

    public long toReplyCount(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                threadReplyStatusRepository.countByBucketAndResolvedFalse(
                                        ThreadReplyBucket.TO_REPLY));
    }

    /**
     * Unresolved counts for all three needs-reply tabs in one pass, so every tab badge is accurate
     * regardless of which tab is open (the to-reply count keeps its own endpoint for the sidebar).
     */
    public NeedsReplyCounts counts(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                new NeedsReplyCounts(
                                        threadReplyStatusRepository.countByBucketAndResolvedFalse(
                                                ThreadReplyBucket.TO_REPLY),
                                        threadReplyStatusRepository.countByBucketAndResolvedFalse(
                                                ThreadReplyBucket.AWAITING_THEIR_REPLY),
                                        threadReplyStatusRepository
                                                .countByBucketAndResolvedFalseAndHasDraftTrue(
                                                        ThreadReplyBucket.TO_REPLY)));
    }

    private static String cursorFor(NeedsReplyRow row) {
        if (row.lastClassifiedAt() == null) {
            return KeysetCursor.nullsLast(row.gmailThreadId());
        }
        return KeysetCursor.encode(row.lastClassifiedAt(), row.gmailThreadId());
    }
}
