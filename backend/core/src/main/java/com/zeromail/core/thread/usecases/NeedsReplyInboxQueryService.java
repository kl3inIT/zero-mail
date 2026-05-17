package com.zeromail.core.thread.usecases;

import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.persistence.ThreadReplyStatusRepository;
import com.zeromail.core.thread.projection.NeedsReplyPage;
import com.zeromail.core.thread.projection.NeedsReplyPageQuery;
import com.zeromail.core.thread.projection.NeedsReplyRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NeedsReplyInboxQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ThreadReplyStatusRepository threadReplyStatusRepository;

    public NeedsReplyInboxQueryService(
            JdbcTemplate jdbcTemplate, ThreadReplyStatusRepository threadReplyStatusRepository) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
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

        ArrayList<Object> parameters = new ArrayList<>();
        StringBuilder sql =
                new StringBuilder(
                        """
                        select gmail_thread_id, bucket, has_draft, draft_id, last_classified_at, resolved
                        from thread_reply_status
                        where tenant_id = ?
                        """);
        parameters.add(tenantId);
        if (pageQuery.resolvedOnly()) {
            sql.append(" and resolved = true");
        } else {
            sql.append(" and bucket = ? and resolved = false");
            parameters.add(pageQuery.bucket().id());
        }
        if (decodedCursor.isPresent()) {
            appendCursorPredicate(sql, parameters, decodedCursor.orElseThrow());
        }
        sql.append(" order by last_classified_at desc nulls last, gmail_thread_id desc limit ?");
        parameters.add(pageQuery.limit() + 1);

        List<NeedsReplyRow> rows =
                jdbcTemplate.query(
                        sql.toString(),
                        (resultSet, _) -> {
                            Timestamp classifiedAtTimestamp =
                                    resultSet.getTimestamp("last_classified_at");
                            ThreadReplyBucket bucket =
                                    ThreadReplyBucket.fromId(resultSet.getString("bucket"));
                            return new NeedsReplyRow(
                                    resultSet.getString("gmail_thread_id"),
                                    bucket.publicSlug(),
                                    resultSet.getBoolean("has_draft"),
                                    resultSet.getString("draft_id"),
                                    classifiedAtTimestamp == null
                                            ? null
                                            : classifiedAtTimestamp.toInstant(),
                                    resultSet.getBoolean("resolved"));
                        },
                        parameters.toArray());
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

    private static void appendCursorPredicate(
            StringBuilder sql, ArrayList<Object> parameters, KeysetCursor cursor) {
        if (cursor.isNullsLast()) {
            sql.append(" and last_classified_at is null and gmail_thread_id < ?");
            parameters.add(cursor.id());
            return;
        }
        Instant timestamp = cursor.timestamp();
        sql.append(
                """
                 and (
                    last_classified_at < ?
                    or (last_classified_at = ? and gmail_thread_id < ?)
                    or last_classified_at is null
                 )
                """);
        parameters.add(Timestamp.from(timestamp));
        parameters.add(Timestamp.from(timestamp));
        parameters.add(cursor.id());
    }

    private static String cursorFor(NeedsReplyRow row) {
        if (row.lastClassifiedAt() == null) {
            return KeysetCursor.nullsLast(row.gmailThreadId());
        }
        return KeysetCursor.encode(row.lastClassifiedAt(), row.gmailThreadId());
    }
}
