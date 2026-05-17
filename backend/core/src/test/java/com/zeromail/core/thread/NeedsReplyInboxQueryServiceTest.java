package com.zeromail.core.thread;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.projection.NeedsReplyPage;
import com.zeromail.core.thread.projection.NeedsReplyPageQuery;
import com.zeromail.core.thread.usecases.NeedsReplyInboxQueryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class NeedsReplyInboxQueryServiceTest extends PostgresContainerTest {

    @Autowired NeedsReplyInboxQueryService needsReplyInboxQueryService;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void page_handles_nulls_last_tail_without_duplicates_or_skips() {
        UUID tenantId = seedTenant("needs-reply-query");
        UUID otherTenantId = seedTenant("needs-reply-other");
        Instant newest = Instant.parse("2026-05-12T12:00:00.000003Z");
        Instant older = Instant.parse("2026-05-12T12:00:00.000002Z");
        insertStatus(
                tenantId, "thread-d", ThreadReplyBucket.TO_REPLY, newest, true, "draft-d", false);
        insertStatus(tenantId, "thread-c", ThreadReplyBucket.TO_REPLY, older, false, null, false);
        insertStatus(tenantId, "thread-z", ThreadReplyBucket.TO_REPLY, null, false, null, false);
        insertStatus(tenantId, "thread-a", ThreadReplyBucket.TO_REPLY, null, false, null, false);
        insertStatus(
                tenantId,
                "thread-resolved",
                ThreadReplyBucket.TO_REPLY,
                newest.plusSeconds(1),
                false,
                null,
                true);
        insertStatus(
                otherTenantId,
                "thread-other",
                ThreadReplyBucket.TO_REPLY,
                newest.plusSeconds(2),
                false,
                null,
                false);

        NeedsReplyPage firstPage =
                needsReplyInboxQueryService.page(
                        tenantId,
                        new NeedsReplyPageQuery(ThreadReplyBucket.TO_REPLY, false, 3, null));

        assertThat(firstPage.items())
                .extracting(row -> row.gmailThreadId())
                .containsExactly("thread-d", "thread-c", "thread-z");
        assertThat(firstPage.items().getFirst().bucket()).isEqualTo("to-reply");
        assertThat(firstPage.items().getFirst().draftId()).isEqualTo("draft-d");
        KeysetCursor firstCursor = KeysetCursor.decode(firstPage.nextCursor()).orElseThrow();
        assertThat(firstCursor.isNullsLast()).isTrue();
        assertThat(firstCursor.id()).isEqualTo("thread-z");

        NeedsReplyPage secondPage =
                needsReplyInboxQueryService.page(
                        tenantId,
                        new NeedsReplyPageQuery(
                                ThreadReplyBucket.TO_REPLY, false, 3, firstPage.nextCursor()));
        assertThat(secondPage.items())
                .extracting(row -> row.gmailThreadId())
                .containsExactly("thread-a");
        assertThat(secondPage.nextCursor()).isNull();

        assertThat(needsReplyInboxQueryService.toReplyCount(tenantId)).isEqualTo(4);
    }

    @Test
    void resolved_only_query_ignores_bucket_filter_and_returns_resolved_rows() {
        UUID tenantId = seedTenant("needs-reply-resolved");
        Instant classifiedAt = Instant.parse("2026-05-12T12:00:00.000001Z");
        insertStatus(
                tenantId,
                "thread-open",
                ThreadReplyBucket.TO_REPLY,
                classifiedAt,
                false,
                null,
                false);
        insertStatus(
                tenantId,
                "thread-done",
                ThreadReplyBucket.AWAITING_THEIR_REPLY,
                classifiedAt.plusSeconds(1),
                false,
                null,
                true);

        NeedsReplyPage page =
                needsReplyInboxQueryService.page(
                        tenantId, new NeedsReplyPageQuery(null, true, 10, null));

        assertThat(page.items())
                .extracting(row -> row.gmailThreadId())
                .containsExactly("thread-done");
        assertThat(page.items().getFirst().resolved()).isTrue();
    }

    private UUID seedTenant(String displayNamePrefix) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                displayNamePrefix + "-" + tenantId);
        return tenantId;
    }

    private void insertStatus(
            UUID tenantId,
            String gmailThreadId,
            ThreadReplyBucket bucket,
            Instant lastClassifiedAt,
            boolean hasDraft,
            String draftId,
            boolean resolved) {
        jdbcTemplate.update(
                """
                insert into thread_reply_status(
                    tenant_id, gmail_thread_id, bucket, last_classified_message_id,
                    last_classified_at, has_draft, draft_id, resolved
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                gmailThreadId,
                bucket.id(),
                "message-" + gmailThreadId,
                lastClassifiedAt == null ? null : Timestamp.from(lastClassifiedAt),
                hasDraft,
                draftId,
                resolved);
    }
}
