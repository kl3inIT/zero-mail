package com.zeromail.core.admin.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.billing.projection.AdminBillingFeaturePermissionRow;
import com.zeromail.core.admin.billing.projection.AdminBillingPackageSnapshot;
import com.zeromail.core.admin.billing.projection.AdminBillingPaymentRow;
import com.zeromail.core.admin.billing.usecases.AdminBillingPackageCommandService;
import com.zeromail.core.admin.billing.usecases.AdminBillingPackageQueryService;
import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.usecases.FeatureCatalogCache;
import com.zeromail.core.support.PostgresContainerTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminBillingPackageQueryServiceTest extends PostgresContainerTest {

    private static final String TEST_FEATURE_CODE = "ADMIN_BILLING_TEST_FEATURE";

    @Autowired private AdminBillingPackageCommandService adminBillingPackageCommandService;
    @Autowired private AdminBillingPackageQueryService adminBillingPackageQueryService;
    @Autowired private FeatureCatalogCache featureCatalogCache;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanRows() {
        jdbcTemplate.execute("TRUNCATE TABLE tenants RESTART IDENTITY CASCADE");
        jdbcTemplate.update(
                "DELETE FROM plan_feature_permission WHERE feature_code = ?", TEST_FEATURE_CODE);
        jdbcTemplate.update("DELETE FROM feature_catalog WHERE code = ?", TEST_FEATURE_CODE);
    }

    @Test
    void snapshot_reads_plan_cards_and_fixed_feature_costs_with_plan_toggles() {
        seedFeature();
        UUID freePlanId = planId("FREE");
        UUID plusPlanId = planId("PLUS");
        UUID proPlanId = planId("PRO");
        seedPermission(freePlanId, false);
        seedPermission(plusPlanId, true);
        seedPermission(proPlanId, true);

        AdminBillingPackageSnapshot snapshot = adminBillingPackageQueryService.snapshot();

        assertThat(snapshot.plans())
                .extracting(plan -> plan.code())
                .contains("FREE", "PLUS", "PRO");
        assertThat(snapshot.plans())
                .filteredOn(plan -> plan.code().equals("PLUS"))
                .singleElement()
                .satisfies(
                        plan -> {
                            assertThat(plan.priceVnd()).isEqualTo(199_000L);
                            assertThat(plan.monthlyCreditAllowance()).isGreaterThan(0);
                        });

        AdminBillingFeaturePermissionRow featureRow =
                snapshot.featurePermissions().stream()
                        .filter(feature -> feature.featureCode().equals(TEST_FEATURE_CODE))
                        .findFirst()
                        .orElseThrow();
        assertThat(featureRow.fixedCreditCost()).isEqualTo(7);
        assertThat(featureRow.unitLabel()).isEqualTo("credit/lần gọi");
        assertThat(featureRow.planPermissions())
                .extracting(permission -> permission.planCode() + ":" + permission.enabled())
                .contains("FREE:false", "PLUS:true", "PRO:true");
    }

    @Test
    void snapshot_includes_paid_periods_and_pending_bank_transfer_intents_as_payment_history() {
        UUID tenantId = UUID.fromString("00000000-0000-4000-8000-00000000b701");
        seedTenant(tenantId, "Acme Company", "owner@acme.test");
        UUID plusPlanId = planId("PLUS");
        UUID proPlanId = planId("PRO");
        seedPaidPeriod(
                tenantId,
                plusPlanId,
                "LEMON_SQUEEZY",
                "ls-order-123",
                Instant.parse("2042-03-01T08:00:00Z"),
                199_000L);
        seedPendingBankTransfer(
                tenantId,
                proPlanId,
                "PRO",
                "ZMTEST01",
                Instant.parse("2042-03-02T09:00:00Z"),
                399_000L);

        AdminBillingPackageSnapshot snapshot = adminBillingPackageQueryService.snapshot();

        assertThat(snapshot.paymentHistory())
                .extracting(AdminBillingPaymentRow::status)
                .contains("PAID", "PENDING");
        assertThat(snapshot.paymentHistory())
                .filteredOn(payment -> payment.status().equals("PAID"))
                .singleElement()
                .satisfies(
                        payment -> {
                            assertThat(payment.customerDisplayName()).isEqualTo("Acme Company");
                            assertThat(payment.customerEmail()).isEqualTo("owner@acme.test");
                            assertThat(payment.planCode()).isEqualTo("PLUS");
                            assertThat(payment.amountVnd()).isEqualTo(199_000L);
                            assertThat(payment.paymentMethod()).isEqualTo("LEMON_SQUEEZY");
                            assertThat(payment.transactionCode()).isEqualTo("ls-order-123");
                        });
    }

    @Test
    void setPlanFeaturePermissionEnabled_toggles_existing_plan_feature_permission() {
        seedFeature();
        UUID freePlanId = planId("FREE");
        seedPermission(freePlanId, false);

        adminBillingPackageCommandService.setPlanFeaturePermissionEnabled(
                TEST_FEATURE_CODE, "FREE", true);

        Boolean storedEnabled =
                jdbcTemplate.queryForObject(
                        """
                        SELECT enabled
                        FROM plan_feature_permission
                        WHERE plan_id = ?
                          AND feature_code = ?
                        """,
                        Boolean.class,
                        freePlanId,
                        TEST_FEATURE_CODE);
        assertThat(storedEnabled).isTrue();
        assertThat(adminBillingPackageQueryService.snapshot().featurePermissions())
                .filteredOn(feature -> feature.featureCode().equals(TEST_FEATURE_CODE))
                .singleElement()
                .satisfies(
                        feature ->
                                assertThat(feature.planPermissions())
                                        .filteredOn(
                                                permission -> permission.planCode().equals("FREE"))
                                        .singleElement()
                                        .satisfies(
                                                permission ->
                                                        assertThat(permission.enabled()).isTrue()));
    }

    @Test
    void setFeatureCreditCost_updates_feature_catalog_and_refreshes_runtime_cache() {
        int originalCreditCost =
                jdbcTemplate.queryForObject(
                        "SELECT default_credit_cost FROM feature_catalog WHERE code = ?",
                        Integer.class,
                        CallSite.TRIAGE.id());

        try {
            adminBillingPackageCommandService.setFeatureCreditCost(CallSite.TRIAGE.id(), 9);

            Integer storedCreditCost =
                    jdbcTemplate.queryForObject(
                            "SELECT default_credit_cost FROM feature_catalog WHERE code = ?",
                            Integer.class,
                            CallSite.TRIAGE.id());
            assertThat(storedCreditCost).isEqualTo(9);
            assertThat(featureCatalogCache.defaultCost(CallSite.TRIAGE)).isEqualTo(9);
            assertThat(adminBillingPackageQueryService.snapshot().featurePermissions())
                    .filteredOn(feature -> feature.featureCode().equals(CallSite.TRIAGE.id()))
                    .singleElement()
                    .satisfies(feature -> assertThat(feature.fixedCreditCost()).isEqualTo(9));
        } finally {
            jdbcTemplate.update(
                    "UPDATE feature_catalog SET default_credit_cost = ?, updated_at = now() WHERE code = ?",
                    originalCreditCost,
                    CallSite.TRIAGE.id());
            featureCatalogCache.refresh();
        }
    }

    private void seedFeature() {
        jdbcTemplate.update(
                """
                INSERT INTO feature_catalog(
                    code, display_name, description, category, default_credit_cost,
                    active, sort_order
                )
                VALUES (?, ?, ?, 'TRIAGE', 7, true, 999)
                """,
                TEST_FEATURE_CODE,
                "Admin billing test feature",
                "Feature used by admin billing package tests.");
    }

    private UUID planId(String planCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM billing_plan WHERE code = ?", UUID.class, planCode);
    }

    private void seedPermission(UUID planId, boolean enabled) {
        jdbcTemplate.update(
                """
                INSERT INTO plan_feature_permission(
                    plan_id, feature_code, enabled, credit_cost_override,
                    daily_invocation_limit, monthly_invocation_limit
                )
                VALUES (?, ?, ?, NULL, NULL, NULL)
                """,
                planId,
                TEST_FEATURE_CODE,
                enabled);
    }

    private void seedTenant(UUID tenantId, String displayName, String ownerEmail) {
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, displayName);
        jdbcTemplate.update(
                """
                INSERT INTO users(id, tenant_id, google_subject, email, onboarding_step)
                VALUES (?, ?, ?, ?, 'SIGNED_IN')
                """,
                UUID.randomUUID(),
                tenantId,
                "subject-" + tenantId,
                ownerEmail);
    }

    private void seedPaidPeriod(
            UUID tenantId,
            UUID planId,
            String provider,
            String providerOrderId,
            Instant paidAt,
            long amountVnd) {
        jdbcTemplate.update(
                """
                INSERT INTO billing_plan_period(
                    id, tenant_id, plan_id, status, provider, provider_order_id,
                    effective_at, expires_at, paid_at, amount_vnd, currency
                )
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, 'VND')
                """,
                UUID.randomUUID(),
                tenantId,
                planId,
                provider,
                providerOrderId,
                Timestamp.from(paidAt),
                Timestamp.from(paidAt.plusSeconds(30L * 24L * 60L * 60L)),
                Timestamp.from(paidAt),
                amountVnd);
    }

    private void seedPendingBankTransfer(
            UUID tenantId,
            UUID planId,
            String planCode,
            String transferCode,
            Instant createdAt,
            long amountVnd) {
        jdbcTemplate.update(
                """
                INSERT INTO billing_bank_transfer_intent(
                    id, tenant_id, plan_id, plan_code_snapshot, user_email, provider, code,
                    amount_vnd, currency, status, expires_at, bank_code_snapshot,
                    bank_name_snapshot, account_number_snapshot, account_name_snapshot,
                    transfer_content_snapshot, qr_url_snapshot, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'owner@acme.test', 'SEPAY', ?, ?, 'VND', 'PENDING',
                        ?, 'MBBANK', 'MB Bank', '123456789', 'ZERO MAIL',
                        ?, 'https://example.test/qr', ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                planId,
                planCode,
                transferCode,
                amountVnd,
                Timestamp.from(createdAt.plusSeconds(900)),
                "ZM " + transferCode + " " + planCode,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }
}
