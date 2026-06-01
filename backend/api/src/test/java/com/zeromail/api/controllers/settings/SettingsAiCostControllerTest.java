package com.zeromail.api.controllers.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class SettingsAiCostControllerTest extends ApiPostgresTestBase {

    @LocalServerPort int serverPort;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void cleanAuditRows() {
        jdbcTemplate.execute("TRUNCATE TABLE llm_call_audit RESTART IDENTITY CASCADE");
    }

    @Test
    void sevenDayCostReturnsSingleUsdField() throws Exception {
        Seed seed = seedUser("settings-ai-cost");
        Instant now = Instant.now();
        insertAudit(seed.tenantId(), new BigDecimal("1.20"), now.minus(Duration.ofDays(1)), 1);
        insertAudit(seed.tenantId(), new BigDecimal("2.30"), now.minus(Duration.ofDays(2)), 2);
        insertAudit(seed.tenantId(), new BigDecimal("9.99"), now.minus(Duration.ofDays(9)), 3);

        ResponseEntity<String> response =
                authenticatedClient(seed)
                        .get()
                        .uri("/api/settings/ai/cost?window=7d")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.path("usd").decimalValue()).isEqualByComparingTo("3.50");
    }

    @Test
    void costRejectsUnsupportedWindow() throws Exception {
        Seed seed = seedUser("settings-ai-cost-window");

        ResponseEntity<String> response =
                authenticatedClient(seed)
                        .get()
                        .uri("/api/settings/ai/cost?window=30d")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("code").asString()).isEqualTo("ai.cost.window_invalid");
    }

    private RestClient authenticatedClient(Seed seed) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .defaultHeader(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                .defaultHeader(TestSessionSupport.HEADER_EMAIL, seed.email())
                .build();
    }

    private Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        String uniqueLabel = label + "-" + UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, uniqueLabel));
        String googleSubject = "sub-" + uniqueLabel;
        String email = uniqueLabel + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                userRepository.save(
                                        new UserEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                googleSubject,
                                                email)));
        return new Seed(tenantId, googleSubject, email);
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

    private record Seed(UUID tenantId, String googleSubject, String email) {}
}
