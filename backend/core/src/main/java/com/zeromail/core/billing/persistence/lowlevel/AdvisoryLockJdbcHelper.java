package com.zeromail.core.billing.persistence.lowlevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Acquires a transaction-scoped Postgres advisory lock keyed by {@code hashtext(tenantId)}. The
 * lock is released automatically when the surrounding transaction completes.
 */
@Component
public class AdvisoryLockJdbcHelper {

    private final JdbcTemplate jdbcTemplate;

    public AdvisoryLockJdbcHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquireTenantLock(UUID tenantId) {
        jdbcTemplate.execute(
                (Connection connection) -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "SELECT pg_advisory_xact_lock(hashtext(?))")) {
                        statement.setString(1, tenantId.toString());
                        statement.execute();
                    }
                    return null;
                });
    }
}
