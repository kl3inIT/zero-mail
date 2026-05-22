package com.zeromail.core.billing.persistence.lowlevel;

import com.zeromail.core.billing.projection.BillingLedgerEntrySnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BillingLedgerReadRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public BillingLedgerReadRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public int sumAvailableCreditsForCategories(UUID tenantId, List<String> categories) {
        if (categories.isEmpty()) {
            return 0;
        }
        Integer credits =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT COALESCE(SUM(ledger_entry.amount_credits), 0)::int
                          FROM credit_ledger_entry ledger_entry
                          JOIN credit_grant credit_grant_record
                            ON credit_grant_record.id = ledger_entry.grant_id
                         WHERE ledger_entry.tenant_id = :tenantId
                           AND credit_grant_record.category IN (:categories)
                        """,
                        new MapSqlParameterSource(
                                Map.of("tenantId", tenantId, "categories", categories)),
                        Integer.class);
        return credits == null ? 0 : credits;
    }

    public int sumAvailableUnscopedCredits(UUID tenantId) {
        Integer credits =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT COALESCE(SUM(amount_credits), 0)::int
                          FROM credit_ledger_entry
                         WHERE tenant_id = :tenantId
                           AND grant_id IS NULL
                        """,
                        new MapSqlParameterSource("tenantId", tenantId),
                        Integer.class);
        return credits == null ? 0 : credits;
    }

    public List<BillingLedgerEntrySnapshot> findRecentEntries(UUID tenantId, int limit) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("limit", limit);
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       created_at,
                       kind,
                       amount_credits,
                       ref_type,
                       SUM(amount_credits) OVER (
                           PARTITION BY tenant_id
                           ORDER BY created_at ASC, id ASC
                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       )::int AS balance_after_credits
                  FROM credit_ledger_entry
                 WHERE tenant_id = :tenantId
                 ORDER BY created_at DESC, id DESC
                 LIMIT :limit
                """,
                parameters,
                BillingLedgerReadRepository::mapEntry);
    }

    private static BillingLedgerEntrySnapshot mapEntry(ResultSet resultSet, int rowNumber)
            throws SQLException {
        String kind = resultSet.getString("kind");
        String reference = resultSet.getString("ref_type");
        return new BillingLedgerEntrySnapshot(
                resultSet.getObject("id", UUID.class),
                instantOrNull(resultSet.getTimestamp("created_at")),
                kind,
                descriptionFor(kind),
                resultSet.getInt("amount_credits"),
                resultSet.getInt("balance_after_credits"),
                reference);
    }

    private static String descriptionFor(String kind) {
        return switch (kind) {
            case "TOPUP" -> "Credit top-up";
            case "GRANT" -> "Credit grant";
            case "RESERVE" -> "Credit hold";
            case "SETTLE" -> "Credit spent";
            case "RELEASE" -> "Credit released";
            case "EXPIRE" -> "Credit expired";
            case "ADJUSTMENT" -> "Credit adjustment";
            default -> "Credit activity";
        };
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
