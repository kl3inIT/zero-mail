package com.zeromail.core.gmail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class DuplicateActiveEmailTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void connected_mailboxes_with_different_emails_coexist_for_one_tenant() {
        UUID tenantId = seedTenant();

        insertConnection(tenantId, "first@example.test", "CONNECTED", false);
        insertConnection(tenantId, "second@example.test", "CONNECTED", false);

        Long connectedCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from gmail_connections where tenant_id = ? and status = 'CONNECTED'",
                        Long.class,
                        tenantId);
        assertThat(connectedCount).isEqualTo(2L);
    }

    @Test
    void duplicate_active_email_fails_on_partial_unique_index() {
        UUID tenantId = seedTenant();
        insertConnection(tenantId, "Same.Email@example.test", "CONNECTED", false);

        assertThatThrownBy(
                        () ->
                                insertConnection(
                                        tenantId, "same.email@example.test", "CONNECTED", false))
                .isInstanceOfAny(DataIntegrityViolationException.class, SQLException.class)
                .satisfies(
                        thrown ->
                                assertThat(rootCause(thrown).getMessage())
                                        .contains("uq_gmail_conn_active_email"));
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "duplicate-active-email");
        return tenantId;
    }

    private void insertConnection(
            UUID tenantId, String googleEmail, String gmailStatus, boolean primary) {
        jdbcTemplate.update(
                """
                insert into gmail_connections(id, tenant_id, google_email, status, is_primary)
                values (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                googleEmail,
                gmailStatus,
                primary);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable currentThrowable = throwable;
        while (currentThrowable.getCause() != null) {
            currentThrowable = currentThrowable.getCause();
        }
        return currentThrowable;
    }
}
