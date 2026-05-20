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
        List<UUID> tenantIds =
                jdbcTemplate.query(
                        """
                        SELECT tenant_id
                        FROM gmail_connections
                        WHERE LOWER(google_email) = ?
                          AND status = 'CONNECTED'
                        LIMIT 1
                        """,
                        (resultSet, _) -> resultSet.getObject("tenant_id", UUID.class),
                        emailAddress.toLowerCase(Locale.ROOT));
        return tenantIds.stream().findFirst();
    }
}
