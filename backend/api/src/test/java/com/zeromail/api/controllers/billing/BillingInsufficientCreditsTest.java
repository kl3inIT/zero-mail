package com.zeromail.api.controllers.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.usecases.CreditLedger;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import({
    TestSessionSupport.class,
    BillingInsufficientCreditsTest.BillingReserveProbeController.class
})
class BillingInsufficientCreditsTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void insufficient_balance_returns_402_no_balance_leak() throws Exception {
        Seed seed = seedUser("billing-insufficient");

        ResponseEntity<String> response =
                RestClient.create("http://localhost:" + port)
                        .post()
                        .uri("/test/billing/reserve-triage")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(402);
        JsonNode responseJson = objectMapper.readTree(response.getBody());
        assertThat(responseJson.path("code").asString()).isEqualTo("error.billing.insufficient");
        assertThat(responseJson.path("params").isObject()).isTrue();
        assertThat(responseJson.path("params").size()).isZero();
        String responseWithoutStatus = responseJson.toString().replace("\"status\":402", "");
        assertThat(responseWithoutStatus).doesNotContainPattern("\\b\\d+\\b");
    }

    private Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, label));
        attachZeroAllowancePlan(tenantId);
        String googleSubject = "sub-" + label;
        String email = label + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                userRepository.save(
                                        new UserEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                googleSubject,
                                                email)));
        testSessionMinter.mint(googleSubject, email);
        return new Seed(tenantId, googleSubject, email);
    }

    private void attachZeroAllowancePlan(UUID tenantId) {
        UUID planId = UUID.randomUUID();
        String planCode = "TEST_ZERO_" + tenantId.toString().replace("-", "").substring(0, 16);
        jdbcTemplate.update(
                """
                INSERT INTO billing_plan(
                    id, code, display_name, tier_rank, billing_cycle, currency,
                    price_vnd, monthly_credit_allowance, active, sort_order)
                VALUES (?, ?, ?, 0, 'NONE', 'VND', 0, 0, true, 0)
                """,
                planId,
                planCode,
                "Test Zero");
        jdbcTemplate.update(
                """
                INSERT INTO plan_feature_permission(id, plan_id, feature_code, enabled)
                SELECT gen_random_uuid(), ?, code, true
                  FROM feature_catalog
                """,
                planId);
        jdbcTemplate.update(
                """
                INSERT INTO billing_plan_period(
                    id, tenant_id, plan_id, status, provider,
                    effective_at, expires_at, paid_at, amount_vnd, currency)
                VALUES (
                    ?, ?, ?, 'ACTIVE', 'ADMIN',
                    CURRENT_TIMESTAMP - INTERVAL '1 minute',
                    CURRENT_TIMESTAMP + INTERVAL '30 days',
                    CURRENT_TIMESTAMP - INTERVAL '1 minute',
                    0, 'VND')
                """,
                UUID.randomUUID(),
                tenantId,
                planId);
    }

    private record Seed(UUID tenantId, String googleSubject, String email) {}

    @RestController
    static class BillingReserveProbeController {

        private final CreditLedger creditLedger;

        BillingReserveProbeController(CreditLedger creditLedger) {
            this.creditLedger = creditLedger;
        }

        @PostMapping("/test/billing/reserve-triage")
        void reserveTriage() {
            UUID tenantId = TenantContext.currentTenantUuid();
            creditLedger.reserve(tenantId, CallSite.TRIAGE);
        }
    }
}
