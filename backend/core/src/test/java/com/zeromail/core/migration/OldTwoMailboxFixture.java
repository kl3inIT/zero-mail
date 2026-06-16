package com.zeromail.core.migration;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Raw-JDBC fixture for Phase 11 mailbox-isolation tests.
 *
 * <p>It seeds one legacy tenant with two CONNECTED Gmail rows, one primary and one secondary, so
 * tests can create same-message-id and cross-account cases without depending on Phase 11 production
 * entities. Raw JDBC is intentional: every assertion observes database state directly and cannot
 * pass through Hibernate's first-level cache.
 */
public final class OldTwoMailboxFixture {

    private final JdbcTemplate jdbcTemplate;

    public OldTwoMailboxFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public SeededMailboxes seedConnectedMailboxes(String label) {
        Objects.requireNonNull(label, "label must not be null");
        UUID tenantId = UUID.randomUUID();
        UUID primaryGmailConnectionId = UUID.randomUUID();
        UUID secondaryGmailConnectionId = UUID.randomUUID();
        String uniqueSuffix = tenantId.toString();
        String primaryEmail = label + "+primary-" + uniqueSuffix + "@example.test";
        String secondaryEmail = label + "+secondary-" + uniqueSuffix + "@example.test";

        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, "tenant-" + label);
        insertMailbox(tenantId, primaryGmailConnectionId, primaryEmail, "CONNECTED", true);
        insertMailbox(tenantId, secondaryGmailConnectionId, secondaryEmail, "CONNECTED", false);
        return new SeededMailboxes(
                tenantId,
                primaryGmailConnectionId,
                secondaryGmailConnectionId,
                primaryEmail,
                secondaryEmail);
    }

    public UUID seedTenant(String label) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, "tenant-" + label);
        return tenantId;
    }

    public UUID insertConnectedMailbox(UUID tenantId, String googleEmail, boolean primary) {
        UUID gmailConnectionId = UUID.randomUUID();
        insertMailbox(tenantId, gmailConnectionId, googleEmail, "CONNECTED", primary);
        return gmailConnectionId;
    }

    public UUID insertDisconnectedMailbox(UUID tenantId, String googleEmail) {
        UUID gmailConnectionId = UUID.randomUUID();
        insertMailbox(tenantId, gmailConnectionId, googleEmail, "DISCONNECTED", false);
        return gmailConnectionId;
    }

    private void insertMailbox(
            UUID tenantId,
            UUID gmailConnectionId,
            String googleEmail,
            String connectionStatus,
            boolean primary) {
        jdbcTemplate.update(
                """
                INSERT INTO gmail_connections(
                    id, tenant_id, google_email, status, connected_at, is_primary
                ) VALUES (?, ?, ?, ?, NOW(), ?)
                """,
                gmailConnectionId,
                tenantId,
                googleEmail,
                connectionStatus,
                primary);
    }

    public record SeededMailboxes(
            UUID tenantId,
            UUID primaryGmailConnectionId,
            UUID secondaryGmailConnectionId,
            String primaryEmail,
            String secondaryEmail) {}
}
