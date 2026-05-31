package com.zeromail.core.admin.spend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.admin.spend.exception.SpendRangeTooWideException;
import com.zeromail.core.admin.spend.projection.FeatureDonutSlice;
import com.zeromail.core.admin.spend.projection.ProviderStackBarRow;
import com.zeromail.core.admin.spend.projection.SpendDashboardSnapshot;
import com.zeromail.core.admin.spend.projection.SpendQuery;
import com.zeromail.core.admin.spend.projection.TopTenantRow;
import com.zeromail.core.admin.spend.usecases.SpendAggregateQueryService;
import com.zeromail.core.support.PostgresContainerTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SpendAggregateQueryServiceTest extends PostgresContainerTest {

    @Autowired private SpendAggregateQueryService spendAggregateQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        // CASCADE so any other test class that leaves rows in tables that FK to `tenants`
        // (users, rules, chat, billing, etc.) does not block this DELETE in CI ordering.
        jdbcTemplate.execute("TRUNCATE TABLE tenants RESTART IDENTITY CASCADE");
    }

    @Test
    void snapshot_returns_kpis_stack_bar_donut_and_top_tenants_with_three_credential_sources() {
        UUID tenantOne = seedTenant("Tenant A", "tenant-a@example.com");
        UUID tenantTwo = seedTenant("Tenant B", "tenant-b@example.com");

        // Seed mixed rows across providers/features/credential sources.
        insertAudit(tenantOne, "OPENAI", "CHAT", "PLATFORM", new BigDecimal("0.50"), 1);
        insertAudit(tenantOne, "OPENAI", "TRIAGE", "BYOK", new BigDecimal("0.30"), 1);
        insertAudit(tenantTwo, "ANTHROPIC", "DRAFT", "PLATFORM", new BigDecimal("1.20"), 1);
        insertAudit(tenantTwo, "ANTHROPIC", "CHAT", "UNKNOWN", new BigDecimal("0.10"), 1);

        Instant now = Instant.now();
        SpendQuery spendQuery =
                new SpendQuery(
                        now.minus(Duration.ofDays(7)),
                        now.plus(Duration.ofMinutes(1)),
                        Optional.empty(),
                        Optional.empty());
        SpendDashboardSnapshot snapshot = spendAggregateQueryService.snapshot(spendQuery);

        // KPIs reflect the 30-day window (all 4 rows).
        assertThat(snapshot.kpis().thirtyDayCallCount()).isEqualTo(4);
        assertThat(snapshot.kpis().thirtyDayPlatformCost())
                .isEqualByComparingTo(new BigDecimal("1.70"));
        assertThat(snapshot.kpis().thirtyDayByokCost())
                .isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(snapshot.kpis().thirtyDayUnknownCost())
                .isEqualByComparingTo(new BigDecimal("0.10"));

        // Stacked bar has 2 (date, provider) groups — today for OPENAI + ANTHROPIC.
        assertThat(snapshot.stackBar())
                .extracting(ProviderStackBarRow::provider)
                .containsExactlyInAnyOrder("OPENAI", "ANTHROPIC");

        // Donut has 3 feature slices.
        assertThat(snapshot.donut())
                .extracting(FeatureDonutSlice::feature)
                .containsExactlyInAnyOrder("CHAT", "TRIAGE", "DRAFT");

        // Top tenants — both real tenants surface their email; no rollup row because no
        // deleted-tenant rows exist.
        assertThat(snapshot.topTenants())
                .extracting(TopTenantRow::gmailAccountEmailOrPlaceholder)
                .containsExactlyInAnyOrder("tenant-a@example.com", "tenant-b@example.com");
        assertThat(snapshot.topTenants())
                .allSatisfy(row -> assertThat(row.isKAnonymized()).isFalse());

        // Unknown percentage is computed across all stackBar rows: 0.10 / 2.10 ≈ 4.76%.
        assertThat(snapshot.unknownPercentOfTotal())
                .isCloseTo(4.76, org.assertj.core.api.Assertions.within(0.5));

        assertThat(snapshot.kAnonymityFooterNote())
                .contains("k≥5")
                .contains("Exact per-tenant cost is not exposed");
    }

    @Test
    void spend_query_rejects_range_over_90_days() {
        Instant now = Instant.now();
        assertThatThrownBy(
                        () ->
                                new SpendQuery(
                                        now.minus(Duration.ofDays(120)),
                                        now,
                                        Optional.empty(),
                                        Optional.empty()))
                .isInstanceOf(SpendRangeTooWideException.class);
    }

    @Test
    void deleted_tenant_rows_collapse_into_rollup_with_k_anonymity_marker() {
        // Tenants exist but with NULL gmail_connection (deleted/disconnected).
        UUID activeTenant = seedTenant("Active", "active@example.com");
        UUID orphanTenant = UUID.randomUUID();
        // Insert tenant row but no gmail_connections entry → joins as NULL email.
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                orphanTenant,
                "Orphan tenant");

        insertAudit(activeTenant, "OPENAI", "CHAT", "PLATFORM", new BigDecimal("1.00"), 1);
        // Orphan-tenant rows
        insertAudit(orphanTenant, "OPENAI", "CHAT", "PLATFORM", new BigDecimal("0.20"), 1);
        insertAudit(orphanTenant, "OPENAI", "TRIAGE", "BYOK", new BigDecimal("0.30"), 1);

        Instant now = Instant.now();
        SpendQuery spendQuery =
                new SpendQuery(
                        now.minus(Duration.ofDays(7)),
                        now.plus(Duration.ofMinutes(1)),
                        Optional.empty(),
                        Optional.empty());
        SpendDashboardSnapshot snapshot = spendAggregateQueryService.snapshot(spendQuery);

        // One real tenant row + one rollup (orphan tenant, 1 < k=5 → K-anonymized aggregate).
        assertThat(snapshot.topTenants()).hasSize(2);
        TopTenantRow rollup =
                snapshot.topTenants().stream()
                        .filter(TopTenantRow::isKAnonymized)
                        .findFirst()
                        .orElseThrow();
        assertThat(rollup.tenantId()).isNull();
        assertThat(rollup.gmailAccountEmailOrPlaceholder()).contains("K-anonymized aggregate");
        assertThat(rollup.totalCost()).isEqualByComparingTo(new BigDecimal("0.50"));
    }

    @Test
    void provider_filter_restricts_aggregates() {
        UUID tenantOne = seedTenant("Tenant A", "tenant-a@example.com");
        insertAudit(tenantOne, "OPENAI", "CHAT", "PLATFORM", new BigDecimal("0.50"), 1);
        insertAudit(tenantOne, "ANTHROPIC", "CHAT", "PLATFORM", new BigDecimal("1.00"), 1);

        Instant now = Instant.now();
        SpendQuery anthropicOnly =
                new SpendQuery(
                        now.minus(Duration.ofDays(7)),
                        now.plus(Duration.ofMinutes(1)),
                        Optional.of(
                                java.util.Set.of(
                                        com.zeromail.core.llm.domain.LlmProvider.ANTHROPIC)),
                        Optional.empty());
        SpendDashboardSnapshot snapshot = spendAggregateQueryService.snapshot(anthropicOnly);

        assertThat(snapshot.stackBar()).hasSize(1);
        assertThat(snapshot.stackBar().get(0).provider()).isEqualTo("ANTHROPIC");
        assertThat(snapshot.donut())
                .extracting(FeatureDonutSlice::totalCost)
                .containsExactly(new BigDecimal("1.000000"));
    }

    private UUID seedTenant(String displayName, String gmailEmail) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, displayName);
        jdbcTemplate.update(
                "INSERT INTO gmail_connections(id, tenant_id, google_email, status)"
                        + " VALUES (?, ?, ?, 'CONNECTED')",
                UUID.randomUUID(),
                tenantId,
                gmailEmail);
        return tenantId;
    }

    private void insertAudit(
            UUID tenantId,
            String provider,
            String feature,
            String credentialSource,
            BigDecimal costUsd,
            int callIndex) {
        jdbcTemplate.update(
                """
                INSERT INTO llm_call_audit(
                    id, tenant_id, provider, feature, model_id, credential_source,
                    prompt_tokens, completion_tokens, total_cost_usd, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """,
                UUID.randomUUID(),
                tenantId,
                provider,
                feature,
                provider.toLowerCase() + "/test-model",
                credentialSource,
                100 + callIndex,
                50 + callIndex,
                costUsd);
    }
}
