package com.zeromail.core.llm.cost;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AiCostQueryService7DayTest extends PostgresContainerTest {

    @Autowired private AiCostQueryService aiCostQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        // gmail_connections has no FK to tenants, so CASCADE does not reach it; truncate it
        // explicitly or the global active-email unique (changeset 127) collides on re-seed.
        jdbcTemplate.execute("TRUNCATE TABLE tenants, gmail_connections RESTART IDENTITY CASCADE");
    }

    @Test
    void sevenDayCostReturnsSingleTenantWideUsdValue() {
        UUID tenantId = seedTenant("cost-window-tenant");
        UUID otherTenantId = seedTenant("cost-other-tenant");
        Instant now = Instant.now();
        insertAudit(tenantId, new BigDecimal("1.10"), now.minus(Duration.ofDays(1)), 1);
        insertAudit(tenantId, new BigDecimal("2.20"), now.minus(Duration.ofDays(3)), 2);
        insertAudit(tenantId, new BigDecimal("3.30"), now.minus(Duration.ofDays(5)), 3);
        insertAudit(tenantId, new BigDecimal("4.40"), now.minus(Duration.ofDays(8)), 4);
        insertAudit(tenantId, new BigDecimal("5.50"), now.minus(Duration.ofDays(10)), 5);
        insertAudit(otherTenantId, new BigDecimal("99.99"), now.minus(Duration.ofDays(1)), 6);

        BigDecimal totalUsd = aiCostQueryService.totalUsdLast7Days(tenantId);

        assertThat(totalUsd).isEqualByComparingTo("6.60");
        assertThat(totalUsd.scale()).isEqualTo(2);
    }

    @Test
    void zeroRowTenantReturnsZeroUsd() {
        UUID tenantId = seedTenant("cost-zero-tenant");

        BigDecimal totalUsd = aiCostQueryService.totalUsdLast7Days(tenantId);

        assertThat(totalUsd).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totalUsd.scale()).isEqualTo(2);
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, displayName);
        return tenantId;
    }

    private void insertAudit(UUID tenantId, BigDecimal costUsd, Instant createdAt, int callIndex) {
        jdbcTemplate.update(
                """
                INSERT INTO llm_call_audit(
                    id, tenant_id, provider, feature, model_id, credential_source,
                    prompt_tokens, completion_tokens, total_cost_usd, created_at)
                VALUES (?, ?, 'OPENAI', 'CHAT', 'gpt-4o-mini', 'PLATFORM', ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                100 + callIndex,
                50 + callIndex,
                costUsd,
                Timestamp.from(createdAt));
    }
}
