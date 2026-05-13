package com.zeromail.core.thread;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.usecases.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ThreadReplyStatusAccountDeletionCascadeTest extends PostgresContainerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000512");

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired TenantService tenantService;

    @Test
    void account_deletion_removes_thread_reply_status_rows_through_tenant_fk_cascade() {
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?) on conflict (id) do nothing",
                TENANT_ID,
                "thread-reply-cascade");
        jdbcTemplate.update(
                """
                insert into thread_reply_status(
                  tenant_id, gmail_thread_id, bucket, last_classified_message_id,
                  last_classified_at, has_draft, draft_id, resolved
                )
                values (?, ?, 'TO_REPLY', ?, now(), false, null, false)
                """,
                TENANT_ID,
                "gmail-thread-cascade",
                "gmail-message-cascade");

        tenantService.deleteCurrentTenant(TENANT_ID);

        Integer remainingRows =
                jdbcTemplate.queryForObject(
                        "select count(*) from thread_reply_status where tenant_id = ?",
                        Integer.class,
                        TENANT_ID);
        assertThat(remainingRows).isZero();
    }
}
