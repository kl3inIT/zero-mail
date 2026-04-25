package com.zeromail.core.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.core.support.PostgresContainerTest;

/**
 * AUTH-02 enforcement: only one Gmail connection per tenant. The unique constraint
 * uq_gmail_connections_tenant_id (changeset 003) must reject the second insert.
 */
class GmailConnectionUniquenessTest extends PostgresContainerTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void second_insert_for_same_tenant_fails() {
        UUID tenantId = UUID.randomUUID();
        jdbc.update("insert into tenants(id, display_name) values(?, ?)", tenantId, "T");
        jdbc.update("insert into gmail_connections(id, tenant_id, google_email, status) values(?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, "a@example.com", "CONNECTED");

        assertThatThrownBy(() ->
                jdbc.update("insert into gmail_connections(id, tenant_id, google_email, status) values(?, ?, ?, ?)",
                        UUID.randomUUID(), tenantId, "b@example.com", "CONNECTED"))
                .isInstanceOfAny(DataIntegrityViolationException.class, SQLException.class);
    }
}
