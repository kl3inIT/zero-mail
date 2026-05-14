package com.zeromail.core.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.usecases.MarkThreadResolvedService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class MarkThreadResolvedServiceTest extends PostgresContainerTest {

    @Autowired MarkThreadResolvedService markThreadResolvedService;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void mark_resolved_flips_only_current_tenant_row_and_missing_row_is_noop() {
        UUID tenantA = seedTenant("mark-resolved-a");
        UUID tenantB = seedTenant("mark-resolved-b");
        insertStatus(tenantA, "shared-thread", false);
        insertStatus(tenantB, "shared-thread", false);

        markThreadResolvedService.markResolved(tenantA, "shared-thread");
        assertThatCode(() -> markThreadResolvedService.markResolved(tenantA, "missing-thread"))
                .doesNotThrowAnyException();

        assertThat(resolved(tenantA, "shared-thread")).isTrue();
        assertThat(resolved(tenantB, "shared-thread")).isFalse();
    }

    private UUID seedTenant(String displayNamePrefix) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                displayNamePrefix + "-" + tenantId);
        return tenantId;
    }

    private void insertStatus(UUID tenantId, String gmailThreadId, boolean resolved) {
        jdbcTemplate.update(
                """
                insert into thread_reply_status(
                    tenant_id, gmail_thread_id, bucket, last_classified_message_id,
                    last_classified_at, has_draft, resolved
                )
                values (?, ?, ?, ?, ?, false, ?)
                """,
                tenantId,
                gmailThreadId,
                ThreadReplyBucket.TO_REPLY.id(),
                "message-" + gmailThreadId,
                Timestamp.from(Instant.parse("2026-05-12T12:00:00Z")),
                resolved);
    }

    private boolean resolved(UUID tenantId, String gmailThreadId) {
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        """
                        select resolved
                        from thread_reply_status
                        where tenant_id = ? and gmail_thread_id = ?
                        """,
                        Boolean.class,
                        tenantId,
                        gmailThreadId));
    }
}
