package com.zeromail.core.admin.billing.persistence.lowlevel;

import com.zeromail.core.admin.billing.projection.AdminBillingFeaturePermissionRow;
import com.zeromail.core.admin.billing.projection.AdminBillingPaymentRow;
import com.zeromail.core.admin.billing.projection.AdminBillingPlanPermission;
import com.zeromail.core.admin.billing.projection.AdminBillingPlanRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminBillingPackageReadRepository {

    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final int PAYMENT_HISTORY_LIMIT = 25;
    private static final String CREDIT_UNIT_LABEL = "credit/lần gọi";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public AdminBillingPackageReadRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
        namedParameterJdbcTemplate.getJdbcTemplate().setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    public List<AdminBillingPlanRow> findPlans() {
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       code,
                       display_name,
                       tier_rank,
                       billing_cycle,
                       currency,
                       price_vnd,
                       monthly_credit_allowance,
                       active,
                       sort_order
                FROM billing_plan
                ORDER BY sort_order ASC, tier_rank ASC, code ASC
                """,
                (resultSet, _) ->
                        new AdminBillingPlanRow(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("code"),
                                resultSet.getString("display_name"),
                                resultSet.getShort("tier_rank"),
                                resultSet.getString("billing_cycle"),
                                resultSet.getString("currency"),
                                resultSet.getLong("price_vnd"),
                                resultSet.getInt("monthly_credit_allowance"),
                                resultSet.getBoolean("active"),
                                resultSet.getInt("sort_order")));
    }

    public List<AdminBillingFeaturePermissionRow> findFeaturePermissions() {
        Map<String, FeaturePermissionAccumulator> accumulators = new LinkedHashMap<>();
        namedParameterJdbcTemplate.query(
                """
                SELECT feature.code AS feature_code,
                       feature.display_name AS feature_display_name,
                       feature.description AS feature_description,
                       feature.category AS feature_category,
                       feature.default_credit_cost AS fixed_credit_cost,
                       feature.sort_order AS feature_sort_order,
                       plan.code AS plan_code,
                       COALESCE(permission.enabled, false) AS permission_enabled
                FROM feature_catalog feature
                CROSS JOIN billing_plan plan
                LEFT JOIN plan_feature_permission permission
                  ON permission.plan_id = plan.id
                 AND permission.feature_code = feature.code
                WHERE feature.active = true
                  AND plan.active = true
                ORDER BY feature.sort_order ASC,
                         feature.code ASC,
                         plan.sort_order ASC,
                         plan.tier_rank ASC,
                         plan.code ASC
                """,
                resultSet -> {
                    String featureCode = resultSet.getString("feature_code");
                    String featureDisplayName = resultSet.getString("feature_display_name");
                    String featureDescription = resultSet.getString("feature_description");
                    String featureCategory = resultSet.getString("feature_category");
                    int fixedCreditCost = resultSet.getInt("fixed_credit_cost");
                    int featureSortOrder = resultSet.getInt("feature_sort_order");
                    FeaturePermissionAccumulator accumulator =
                            accumulators.computeIfAbsent(
                                    featureCode,
                                    _ ->
                                            new FeaturePermissionAccumulator(
                                                    featureCode,
                                                    featureDisplayName,
                                                    featureDescription,
                                                    featureCategory,
                                                    fixedCreditCost,
                                                    featureSortOrder));
                    accumulator.planPermissions.add(
                            new AdminBillingPlanPermission(
                                    resultSet.getString("plan_code"),
                                    resultSet.getBoolean("permission_enabled")));
                });
        return accumulators.values().stream().map(FeaturePermissionAccumulator::toRow).toList();
    }

    public List<AdminBillingPaymentRow> findPaymentHistory() {
        return namedParameterJdbcTemplate.query(
                """
                WITH owner_user AS (
                    SELECT DISTINCT ON (users.tenant_id)
                           users.tenant_id,
                           users.email AS owner_email
                    FROM users
                    ORDER BY users.tenant_id, users.created_at ASC, users.id ASC
                ),
                paid_periods AS (
                    SELECT plan_period.id::text AS payment_id,
                           tenants.id AS tenant_id,
                           tenants.display_name AS customer_display_name,
                           owner_user.owner_email AS customer_email,
                           billing_plan.code AS plan_code,
                           CASE
                               WHEN plan_period.expires_at IS NULL THEN '-'
                               ELSE GREATEST(
                                   1,
                                   CEIL(
                                       EXTRACT(
                                           EPOCH FROM (
                                               plan_period.expires_at - plan_period.effective_at
                                           )
                                       ) / 2592000.0
                                   )::int
                               )::text || ' tháng'
                           END AS period_label,
                           plan_period.amount_vnd AS amount_vnd,
                           plan_period.currency AS currency,
                           plan_period.provider AS payment_method,
                           COALESCE(
                               plan_period.provider_order_id,
                               plan_period.provider_checkout_id,
                               plan_period.provider_event_id,
                               plan_period.id::text
                           ) AS transaction_code,
                           'PAID' AS payment_status,
                           plan_period.paid_at AS paid_at,
                           plan_period.created_at AS created_at,
                           plan_period.paid_at AS event_at
                    FROM billing_plan_period plan_period
                    JOIN tenants ON tenants.id = plan_period.tenant_id
                    JOIN billing_plan ON billing_plan.id = plan_period.plan_id
                    LEFT JOIN owner_user ON owner_user.tenant_id = tenants.id
                ),
                pending_bank_transfers AS (
                    SELECT bank_intent.id::text AS payment_id,
                           tenants.id AS tenant_id,
                           tenants.display_name AS customer_display_name,
                           COALESCE(bank_intent.user_email, owner_user.owner_email)
                               AS customer_email,
                           bank_intent.plan_code_snapshot AS plan_code,
                           '-' AS period_label,
                           bank_intent.amount_vnd AS amount_vnd,
                           bank_intent.currency AS currency,
                           'SEPAY_QR' AS payment_method,
                           COALESCE(
                               bank_intent.provider_transaction_id,
                               bank_intent.code,
                               bank_intent.id::text
                           ) AS transaction_code,
                           bank_intent.status AS payment_status,
                           bank_intent.paid_at AS paid_at,
                           bank_intent.created_at AS created_at,
                           bank_intent.created_at AS event_at
                    FROM billing_bank_transfer_intent bank_intent
                    JOIN tenants ON tenants.id = bank_intent.tenant_id
                    LEFT JOIN owner_user ON owner_user.tenant_id = tenants.id
                    WHERE bank_intent.status <> 'PAID'
                )
                SELECT payment_id,
                       tenant_id,
                       customer_display_name,
                       customer_email,
                       plan_code,
                       period_label,
                       amount_vnd,
                       currency,
                       payment_method,
                       transaction_code,
                       payment_status,
                       paid_at,
                       created_at
                FROM (
                    SELECT * FROM paid_periods
                    UNION ALL
                    SELECT * FROM pending_bank_transfers
                ) payment_rows
                ORDER BY event_at DESC, payment_id DESC
                LIMIT 25
                """,
                (resultSet, _) -> mapPayment(resultSet));
    }

    private static AdminBillingPaymentRow mapPayment(ResultSet resultSet) throws SQLException {
        return new AdminBillingPaymentRow(
                resultSet.getString("payment_id"),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("customer_display_name"),
                resultSet.getString("customer_email"),
                resultSet.getString("plan_code"),
                resultSet.getString("period_label"),
                resultSet.getLong("amount_vnd"),
                resultSet.getString("currency"),
                resultSet.getString("payment_method"),
                resultSet.getString("transaction_code"),
                resultSet.getString("payment_status"),
                instantOrNull(resultSet, "paid_at"),
                instantOrNull(resultSet, "created_at"));
    }

    private static Instant instantOrNull(ResultSet resultSet, String columnLabel)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnLabel);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record FeaturePermissionAccumulator(
            String featureCode,
            String displayName,
            String description,
            String category,
            int fixedCreditCost,
            int sortOrder,
            List<AdminBillingPlanPermission> planPermissions) {

        FeaturePermissionAccumulator(
                String featureCode,
                String displayName,
                String description,
                String category,
                int fixedCreditCost,
                int sortOrder) {
            this(
                    featureCode,
                    displayName,
                    description,
                    category,
                    fixedCreditCost,
                    sortOrder,
                    new ArrayList<>());
        }

        AdminBillingFeaturePermissionRow toRow() {
            return new AdminBillingFeaturePermissionRow(
                    featureCode,
                    displayName,
                    description,
                    category,
                    fixedCreditCost,
                    CREDIT_UNIT_LABEL,
                    sortOrder,
                    List.copyOf(planPermissions));
        }
    }
}
