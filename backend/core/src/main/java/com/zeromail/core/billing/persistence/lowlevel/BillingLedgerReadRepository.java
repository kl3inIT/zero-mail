package com.zeromail.core.billing.persistence.lowlevel;

import com.zeromail.core.billing.projection.BillingLedgerEntrySnapshot;
import com.zeromail.core.billing.projection.BillingLedgerPage;
import com.zeromail.core.shared.exception.InvalidPaginationCursorException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
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

    public BillingLedgerPage findRecentEntries(UUID tenantId, int limit, String cursor) {
        BillingLedgerCursor billingLedgerCursor = decodeCursor(cursor);
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("queryLimit", limit + 1)
                        .addValue("hasCursor", billingLedgerCursor != null)
                        .addValue(
                                "cursorCreatedAt",
                                billingLedgerCursor == null
                                        ? null
                                        : Timestamp.from(billingLedgerCursor.createdAt()))
                        .addValue(
                                "cursorId",
                                billingLedgerCursor == null ? null : billingLedgerCursor.id());
        List<BillingLedgerEntrySnapshot> ledgerEntries =
                namedParameterJdbcTemplate.query(
                        """
                        WITH tenant_entries AS (
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
                        )
                        SELECT id,
                               created_at,
                               kind,
                               amount_credits,
                               ref_type,
                               balance_after_credits
                          FROM tenant_entries
                         WHERE :hasCursor = FALSE
                            OR created_at < :cursorCreatedAt
                            OR (created_at = :cursorCreatedAt AND id < :cursorId)
                         ORDER BY created_at DESC, id DESC
                         LIMIT :queryLimit
                        """,
                        parameters,
                        BillingLedgerReadRepository::mapEntry);
        boolean hasNextPage = ledgerEntries.size() > limit;
        List<BillingLedgerEntrySnapshot> pageEntries =
                hasNextPage ? ledgerEntries.subList(0, limit) : ledgerEntries;
        String nextCursor =
                hasNextPage ? encodeCursor(pageEntries.get(pageEntries.size() - 1)) : null;
        return new BillingLedgerPage(pageEntries, nextCursor);
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

    private static BillingLedgerCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decodedCursor =
                    new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int delimiterIndex = decodedCursor.indexOf('|');
            if (delimiterIndex <= 0 || delimiterIndex == decodedCursor.length() - 1) {
                throw new IllegalArgumentException("Cursor delimiter missing");
            }
            return new BillingLedgerCursor(
                    Instant.parse(decodedCursor.substring(0, delimiterIndex)),
                    UUID.fromString(decodedCursor.substring(delimiterIndex + 1)));
        } catch (RuntimeException invalidCursor) {
            throw new InvalidPaginationCursorException(invalidCursor);
        }
    }

    private static String encodeCursor(BillingLedgerEntrySnapshot ledgerEntry) {
        String rawCursor = ledgerEntry.timestamp() + "|" + ledgerEntry.id();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    private record BillingLedgerCursor(Instant createdAt, UUID id) {}
}
