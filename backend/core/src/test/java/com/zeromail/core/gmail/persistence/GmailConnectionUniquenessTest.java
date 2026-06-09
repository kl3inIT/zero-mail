package com.zeromail.core.gmail.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 10 multi-mailbox invariant: one tenant may have multiple active Gmail connections, but not
 * two active rows for the same lowercased Gmail address.
 */
class GmailConnectionUniquenessTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void same_tenant_accepts_different_active_emails_but_rejects_duplicate_active_email() {
        UUID tenantId = UUID.randomUUID();
        jdbc.update("insert into tenants(id, display_name) values(?, ?)", tenantId, "T");
        jdbc.update(
                "insert into gmail_connections(id, tenant_id, google_email, status) values(?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                "a@example.com",
                "CONNECTED");
        jdbc.update(
                "insert into gmail_connections(id, tenant_id, google_email, status) values(?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                "b@example.com",
                "CONNECTED");

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into gmail_connections(id, tenant_id, google_email, status) values(?, ?, ?, ?)",
                                        UUID.randomUUID(),
                                        tenantId,
                                        "A@example.com",
                                        "CONNECTED"))
                .isInstanceOfAny(DataIntegrityViolationException.class, SQLException.class);
    }
}
