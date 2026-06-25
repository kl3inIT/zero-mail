package com.zeromail.core.admin.billing.persistence.lowlevel;

import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminBillingPackageWriteRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public AdminBillingPackageWriteRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
    }

    public boolean setPlanFeaturePermissionEnabled(
            String featureCode, String planCode, boolean enabled) {
        int affectedRows =
                namedParameterJdbcTemplate.update(
                        """
                                INSERT INTO plan_feature_permission(
                                    plan_id, feature_code, enabled, created_at, updated_at, version
                                )
                                SELECT billing_plan.id, feature_catalog.code, :enabled, now(), now(), 0
                                FROM billing_plan
                                JOIN feature_catalog ON feature_catalog.code = :featureCode
                                WHERE billing_plan.code = :planCode
                                ON CONFLICT (plan_id, feature_code) DO UPDATE
                                   SET enabled = EXCLUDED.enabled,
                                       updated_at = now(),
                                       version = plan_feature_permission.version + 1
                                """,
                        Map.of(
                                "featureCode",
                                featureCode,
                                "planCode",
                                planCode,
                                "enabled",
                                enabled));
        return affectedRows > 0;
    }

    public boolean setFeatureCreditCost(String featureCode, int fixedCreditCost) {
        int affectedRows =
                namedParameterJdbcTemplate.update(
                        """
                                UPDATE feature_catalog
                                   SET default_credit_cost = :fixedCreditCost,
                                       updated_at = now(),
                                       version = version + 1
                                 WHERE code = :featureCode
                                """,
                        Map.of("featureCode", featureCode, "fixedCreditCost", fixedCreditCost));
        return affectedRows > 0;
    }
}
