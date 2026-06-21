package com.zeromail.core.referral.persistence.lowlevel;

import com.zeromail.core.referral.projection.ReferralDashboardQuery;
import com.zeromail.core.referral.projection.ReferralLeaderboardRow;
import com.zeromail.core.referral.projection.ReferralTimeSeriesPoint;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReferralDashboardReadRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ReferralDashboardReadRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
    }

    public int totalSuccessfulReferrals(ReferralDashboardQuery referralDashboardQuery) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)::int
                  FROM referral_conversion conversion
                 WHERE conversion.campaign_id = :campaignId
                   AND conversion.qualified_at >= :from
                   AND conversion.qualified_at < :to
                """,
                parameters(referralDashboardQuery),
                Integer.class);
    }

    public int activeReferrerTenants(ReferralDashboardQuery referralDashboardQuery) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT referral_code.owner_tenant_id)::int
                  FROM referral_conversion conversion
                  JOIN referral_code ON referral_code.id = conversion.referral_code_id
                 WHERE conversion.campaign_id = :campaignId
                   AND conversion.qualified_at >= :from
                   AND conversion.qualified_at < :to
                """,
                parameters(referralDashboardQuery),
                Integer.class);
    }

    public List<ReferralLeaderboardRow> leaderboard(ReferralDashboardQuery referralDashboardQuery) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT referral_code.owner_tenant_id AS tenant_id,
                       tenants.display_name AS tenant_display_name,
                       COUNT(conversion.id)::int AS successful_referrals,
                       ROW_NUMBER() OVER (
                           ORDER BY COUNT(conversion.id) DESC, tenants.display_name ASC
                       )::int AS rank
                  FROM referral_conversion conversion
                  JOIN referral_code ON referral_code.id = conversion.referral_code_id
                  JOIN tenants ON tenants.id = referral_code.owner_tenant_id
                 WHERE conversion.campaign_id = :campaignId
                   AND conversion.qualified_at >= :from
                   AND conversion.qualified_at < :to
                 GROUP BY referral_code.owner_tenant_id, tenants.display_name
                 ORDER BY successful_referrals DESC, tenants.display_name ASC
                """,
                parameters(referralDashboardQuery),
                (resultSet, _) ->
                        new ReferralLeaderboardRow(
                                resultSet.getObject("tenant_id", java.util.UUID.class),
                                resultSet.getString("tenant_display_name"),
                                resultSet.getInt("successful_referrals"),
                                resultSet.getInt("rank")));
    }

    public List<ReferralLeaderboardRow> tenantLeaderboardWindow(
            ReferralDashboardQuery referralDashboardQuery, UUID ownerTenantId) {
        return namedParameterJdbcTemplate.query(
                """
                WITH tenant_scores AS (
                    SELECT referral_code.owner_tenant_id AS tenant_id,
                           tenants.display_name AS tenant_display_name,
                           COUNT(conversion.id)::int AS successful_referrals
                      FROM referral_conversion conversion
                      JOIN referral_code ON referral_code.id = conversion.referral_code_id
                      JOIN tenants ON tenants.id = referral_code.owner_tenant_id
                     WHERE conversion.campaign_id = :campaignId
                       AND conversion.qualified_at >= :from
                       AND conversion.qualified_at < :to
                     GROUP BY referral_code.owner_tenant_id, tenants.display_name
                ),
                ranked_tenants AS (
                    SELECT tenant_scores.tenant_id,
                           tenant_scores.tenant_display_name,
                           tenant_scores.successful_referrals,
                           ROW_NUMBER() OVER (
                               ORDER BY tenant_scores.successful_referrals DESC,
                                        tenant_scores.tenant_display_name ASC
                           )::int AS rank
                      FROM tenant_scores
                )
                SELECT tenant_id,
                       tenant_display_name,
                       successful_referrals,
                       rank
                  FROM ranked_tenants
                 ORDER BY rank ASC
                """,
                parameters(referralDashboardQuery).addValue("ownerTenantId", ownerTenantId),
                (resultSet, _) ->
                        new ReferralLeaderboardRow(
                                resultSet.getObject("tenant_id", java.util.UUID.class),
                                resultSet.getString("tenant_display_name"),
                                resultSet.getInt("successful_referrals"),
                                resultSet.getInt("rank")));
    }

    public int rankedTenantCount(ReferralDashboardQuery referralDashboardQuery) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)::int
                  FROM (
                      SELECT referral_code.owner_tenant_id
                        FROM referral_conversion conversion
                        JOIN referral_code ON referral_code.id = conversion.referral_code_id
                       WHERE conversion.campaign_id = :campaignId
                         AND conversion.qualified_at >= :from
                         AND conversion.qualified_at < :to
                       GROUP BY referral_code.owner_tenant_id
                  ) ranked_tenants
                """,
                parameters(referralDashboardQuery),
                Integer.class);
    }

    public List<ReferralTimeSeriesPoint> hourlyTimeSeries(
            ReferralDashboardQuery referralDashboardQuery) {
        return namedParameterJdbcTemplate.query(
                """
                WITH hours AS (
                    SELECT generate_series(
                        date_trunc('hour', CAST(:from AS timestamptz)),
                        date_trunc('hour', CAST(:to AS timestamptz)) - INTERVAL '1 hour',
                        INTERVAL '1 hour'
                    ) AS bucket_start
                ),
                conversion_by_hour AS (
                    SELECT date_trunc('hour', conversion.qualified_at) AS bucket_start,
                           COUNT(*)::int AS successful_referrals
                      FROM referral_conversion conversion
                     WHERE conversion.campaign_id = :campaignId
                       AND conversion.qualified_at >= :from
                       AND conversion.qualified_at < :to
                     GROUP BY date_trunc('hour', conversion.qualified_at)
                )
                SELECT hours.bucket_start,
                       COALESCE(conversion_by_hour.successful_referrals, 0)::int
                           AS successful_referrals
                  FROM hours
                  LEFT JOIN conversion_by_hour USING (bucket_start)
                 ORDER BY hours.bucket_start ASC
                """,
                parameters(referralDashboardQuery),
                (resultSet, _) ->
                        new ReferralTimeSeriesPoint(
                                resultSet.getTimestamp("bucket_start").toInstant(),
                                resultSet.getInt("successful_referrals")));
    }

    public int successfulReferralsForTenant(UUID campaignId, UUID ownerTenantId) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT COUNT(conversion.id)::int
                  FROM referral_conversion conversion
                  JOIN referral_code ON referral_code.id = conversion.referral_code_id
                 WHERE conversion.campaign_id = :campaignId
                   AND referral_code.owner_tenant_id = :ownerTenantId
                """,
                new MapSqlParameterSource()
                        .addValue("campaignId", campaignId)
                        .addValue("ownerTenantId", ownerTenantId),
                Integer.class);
    }

    private static MapSqlParameterSource parameters(ReferralDashboardQuery referralDashboardQuery) {
        return new MapSqlParameterSource()
                .addValue("campaignId", referralDashboardQuery.campaignId())
                .addValue("from", Timestamp.from(referralDashboardQuery.from()))
                .addValue("to", Timestamp.from(referralDashboardQuery.to()));
    }
}
