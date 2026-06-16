package com.zeromail.core.gmail.persistence.lowlevel;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Unscoped tenant lookup by Gmail email address for the ack-fast Pub/Sub push path. Per CONVENTIONS
 * Section 1, the use-case service must not embed SQL.
 *
 * <p>This lookup is intentionally outside any Hibernate tenant filter because the tenant is not yet
 * known when the Pub/Sub envelope arrives. The caller binds TenantContext before opening the
 * tenant-bound INSERT transaction.
 */
@Repository
public class PubSubTenantLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public PubSubTenantLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public Optional<UUID> findConnectedTenantIdByEmail(String emailAddress) {
        return findConnectedMailboxByEmail(emailAddress).map(TenantMailboxRef::tenantId);
    }

    public Optional<TenantMailboxRef> findConnectedMailboxByEmail(String emailAddress) {
        List<TenantMailboxRef> mailboxRefs =
                jdbcTemplate.query(
                        """
                        SELECT tenant_id, id AS gmail_connection_id
                        FROM gmail_connections
                        WHERE LOWER(google_email) = ?
                          AND status = 'CONNECTED'
                        """,
                        (resultSet, _) ->
                                new TenantMailboxRef(
                                        resultSet.getObject("tenant_id", UUID.class),
                                        resultSet.getObject("gmail_connection_id", UUID.class)),
                        emailAddress.toLowerCase(Locale.ROOT));
        if (mailboxRefs.size() > 1) {
            throw new IllegalStateException(
                    "Expected at most one CONNECTED mailbox for lowercased Gmail address");
        }
        return mailboxRefs.stream().findFirst();
    }
}
