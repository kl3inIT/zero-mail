package com.zeromail.core.admin.billing.persistence.lowlevel;

import com.zeromail.core.admin.billing.projection.BillingPackageAdminStats;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BillingPackageAdminReadRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public BillingPackageAdminReadRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Map<UUID, BillingPackageAdminStats> findStatsByPackageId(Collection<UUID> packageIds) {
        if (packageIds == null || packageIds.isEmpty()) {
            return Map.of();
        }
        List<BillingPackageAdminStats> rows =
                namedParameterJdbcTemplate.query(
                        """
                                SELECT package_id,
                                       COUNT(*) FILTER (WHERE status = 'PAID')::bigint AS purchase_count,
                                       COUNT(*) FILTER (WHERE status = 'PENDING')::bigint AS pending_intent_count,
                                       COALESCE(SUM(amount_vnd) FILTER (WHERE status = 'PAID'), 0)::bigint
                                           AS total_revenue_vnd,
                                       MAX(paid_at) FILTER (WHERE status = 'PAID') AS last_purchased_at
                                  FROM billing_topup_intent
                                 WHERE package_id IN (:packageIds)
                                 GROUP BY package_id
                                """,
                        new MapSqlParameterSource("packageIds", packageIds),
                        BillingPackageAdminReadRepository::mapStats);
        return rows.stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                BillingPackageAdminStats::packageId, row -> row));
    }

    private static BillingPackageAdminStats mapStats(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new BillingPackageAdminStats(
                resultSet.getObject("package_id", UUID.class),
                resultSet.getLong("purchase_count"),
                resultSet.getLong("pending_intent_count"),
                resultSet.getLong("total_revenue_vnd"),
                instantOrNull(resultSet.getTimestamp("last_purchased_at")));
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
