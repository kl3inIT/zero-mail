package com.zeromail.core.referral.persistence.lowlevel;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReferralTenantEligibilityRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ReferralTenantEligibilityRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
    }

    public boolean wasTenantCreatedAtOrAfter(UUID tenantId, Instant attributedAt) {
        Boolean eligible =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                              FROM tenants
                             WHERE id = :tenantId
                               AND created_at >= :attributedAt
                        )
                        """,
                        new MapSqlParameterSource()
                                .addValue("tenantId", tenantId)
                                .addValue("attributedAt", Timestamp.from(attributedAt)),
                        Boolean.class);
        return Boolean.TRUE.equals(eligible);
    }
}
