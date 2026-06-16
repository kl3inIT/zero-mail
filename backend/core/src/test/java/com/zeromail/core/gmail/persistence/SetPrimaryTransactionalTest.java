package com.zeromail.core.gmail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SetPrimaryTransactionalTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired GmailConnectionService gmailConnectionService;

    @Test
    void partial_unique_index_forbids_two_primary_mailboxes_for_one_tenant() {
        UUID tenantId = seedTenant();
        insertConnection(tenantId, "primary-a@example.test", true);

        assertThatThrownBy(() -> insertConnection(tenantId, "primary-b@example.test", true))
                .isInstanceOfAny(DataIntegrityViolationException.class, SQLException.class);
    }

    @Test
    void setPrimary_clears_existing_primary_and_sets_requested_mailbox_transactionally() {
        UUID tenantId = seedTenant();
        UUID firstConnectionId = insertConnection(tenantId, "primary-first@example.test", true);
        UUID secondConnectionId = insertConnection(tenantId, "primary-second@example.test", false);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> gmailConnectionService.setPrimary(tenantId, secondConnectionId));

        Boolean firstPrimary = isPrimary(firstConnectionId);
        Boolean secondPrimary = isPrimary(secondConnectionId);
        assertThat(firstPrimary).isFalse();
        assertThat(secondPrimary).isTrue();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, "set-primary");
        return tenantId;
    }

    private UUID insertConnection(UUID tenantId, String googleEmail, boolean primary) {
        UUID gmailConnectionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into gmail_connections(id, tenant_id, google_email, status, is_primary)
                values (?, ?, ?, 'CONNECTED', ?)
                """,
                gmailConnectionId,
                tenantId,
                googleEmail,
                primary);
        return gmailConnectionId;
    }

    private Boolean isPrimary(UUID gmailConnectionId) {
        return jdbcTemplate.queryForObject(
                "select is_primary from gmail_connections where id = ?",
                Boolean.class,
                gmailConnectionId);
    }
}
