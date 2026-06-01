package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.ReservationId;
import com.zeromail.core.billing.exception.InsufficientCreditsException;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.billing.usecases.CreditLedger;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.byok.UserByokKeyEntity;
import com.zeromail.core.llm.byok.UserByokKeyRepository;
import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.exception.SanitizationException;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Import(LlmGatewayCreditLifecycleTest.MeterRegistryTestConfiguration.class)
@TestPropertySource(properties = {"spring.datasource.hikari.maximum-pool-size=12"})
class LlmGatewayCreditLifecycleTest extends PostgresContainerTest {

    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired LlmGateway llmGateway;

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;

    @Autowired UserByokKeyRepository userByokKeyRepository;

    @Autowired RefreshTokenCipher refreshTokenCipher;

    @Autowired MeterRegistry meterRegistry;

    @MockitoSpyBean CreditLedger creditLedger;

    @MockitoBean LlmModelClient platformLlmModelClient;

    @MockitoBean SanitizationPipeline sanitizationPipeline;

    @MockitoBean LlmProviderChatExecutor providerChatExecutor;

    @BeforeEach
    void setUpSanitizer() {
        when(sanitizationPipeline.sanitize(anyString())).thenReturn(sanitizedContext());
        clearInvocations(creditLedger);
    }

    @Test
    void platform_call_reserves_then_settles_on_success() {
        UUID tenantId = seedTenantWithCredits(10);
        when(platformLlmModelClient.call(any())).thenReturn(labelResult("{}"));

        ToolCallResult toolCallResult =
                underTenant(tenantId, () -> llmGateway.chat(CallSite.PREVIEW, "raw"));

        assertThat(toolCallResult.action()).isEqualTo(Action.LABEL);
        verify(creditLedger).reserve(tenantId, CallSite.PREVIEW);
        verify(creditLedger).settle(any(ReservationId.class));
        verify(creditLedger, never()).release(any(ReservationId.class));
        assertUsageAuditRow(tenantId, "PLATFORM", "PREVIEW", 1, 1, 1);
    }

    @Test
    void platform_call_releases_on_safety_violation() {
        UUID tenantId = seedTenantWithCredits(10);
        when(platformLlmModelClient.call(any())).thenReturn(unsafeResult());

        assertThatThrownBy(
                        () -> underTenant(tenantId, () -> llmGateway.chat(CallSite.PREVIEW, "raw")))
                .isInstanceOf(SafetyViolationException.class);

        verify(creditLedger).reserve(tenantId, CallSite.PREVIEW);
        verify(creditLedger).release(any(ReservationId.class));
        verify(creditLedger, never()).settle(any(ReservationId.class));
        Counter absorbedCostCounter =
                meterRegistry
                        .find("llm_safety_violation_cost_absorbed_total")
                        .tag("callSite", CallSite.PREVIEW.id())
                        .counter();
        assertThat(absorbedCostCounter).isNotNull();
        assertThat(absorbedCostCounter.count()).isEqualTo(1.0);
    }

    @Test
    void platform_call_does_not_reserve_on_sanitization_exception() {
        UUID tenantId = seedTenantWithCredits(10);
        when(sanitizationPipeline.sanitize(anyString()))
                .thenThrow(
                        new SanitizationException(
                                "test-sanitizer", new RuntimeException("private")));

        assertThatThrownBy(
                        () -> underTenant(tenantId, () -> llmGateway.chat(CallSite.PREVIEW, "raw")))
                .isInstanceOf(SanitizationException.class);

        verify(creditLedger, never()).reserve(any(), any());
        verify(platformLlmModelClient, never()).call(any());
    }

    @Test
    void byok_path_does_not_touch_ledger() {
        UUID tenantId = seedTenant();
        seedByokCredentials(tenantId);
        when(providerChatExecutor.call(any(LlmProviderCredential.class), any()))
                .thenReturn(labelResult("{}"));

        ToolCallResult toolCallResult =
                underTenant(tenantId, () -> llmGateway.chat(CallSite.PREVIEW, "raw"));

        assertThat(toolCallResult.action()).isEqualTo(Action.LABEL);
        verify(creditLedger, never()).reserve(any(), any());
        verify(creditLedger, never()).settle(any(ReservationId.class));
        verify(creditLedger, never()).release(any(ReservationId.class));
        verify(platformLlmModelClient, never()).call(any());
        verify(providerChatExecutor)
                .call(any(LlmProviderCredential.class), any(LlmChatRequest.class));
        assertUsageAuditRow(tenantId, "BYOK", "PREVIEW", 0, 1, 1);
    }

    @Test
    void insufficient_credits_throws_before_model_call() {
        UUID tenantId = seedTenant();

        assertThatThrownBy(
                        () -> underTenant(tenantId, () -> llmGateway.chat(CallSite.PREVIEW, "raw")))
                .isInstanceOf(InsufficientCreditsException.class);

        verify(creditLedger).reserve(tenantId, CallSite.PREVIEW);
        verify(platformLlmModelClient, never()).call(any());
        verify(creditLedger, never()).settle(any(ReservationId.class));
        verify(creditLedger, never()).release(any(ReservationId.class));
    }

    @Test
    void concurrent_calls_balance_reconciles() throws Exception {
        UUID tenantId = seedTenantWithCredits(CONCURRENT_REQUESTS);
        AtomicInteger modelInvocationIndex = new AtomicInteger();
        when(platformLlmModelClient.call(any()))
                .thenAnswer(
                        _ -> {
                            if (modelInvocationIndex.getAndIncrement() % 2 == 0) {
                                return labelResult("{}");
                            }
                            throw new IllegalStateException("simulated-model-failure");
                        });
        CountDownLatch simultaneousStart = new CountDownLatch(1);

        List<Boolean> results;
        try (var taskScope = StructuredTaskScope.<Boolean>open()) {
            var subtasks =
                    java.util.stream.IntStream.range(0, CONCURRENT_REQUESTS)
                            .mapToObj(
                                    _ ->
                                            taskScope.fork(
                                                    () -> {
                                                        simultaneousStart.await();
                                                        return underTenant(
                                                                tenantId,
                                                                () -> {
                                                                    try {
                                                                        llmGateway.chat(
                                                                                CallSite.TRIAGE,
                                                                                "raw");
                                                                        return true;
                                                                    } catch (
                                                                            RuntimeException
                                                                                    modelFailure) {
                                                                        return false;
                                                                    }
                                                                });
                                                    }))
                            .toList();
            simultaneousStart.countDown();
            taskScope.join();
            results = subtasks.stream().map(StructuredTaskScope.Subtask::get).toList();
        }

        long successfulCalls = results.stream().filter(Boolean::booleanValue).count();
        long failedCalls = CONCURRENT_REQUESTS - successfulCalls;
        assertThat(successfulCalls).isEqualTo(CONCURRENT_REQUESTS / 2);
        assertThat(failedCalls).isEqualTo(CONCURRENT_REQUESTS / 2);
        verify(creditLedger, times(CONCURRENT_REQUESTS)).reserve(tenantId, CallSite.TRIAGE);
        verify(creditLedger, times((int) successfulCalls)).settle(any(ReservationId.class));
        verify(creditLedger, times((int) failedCalls)).release(any(ReservationId.class));
        assertThat(underTenant(tenantId, () -> creditLedger.balance(tenantId).availableCredits()))
                .isEqualTo(CONCURRENT_REQUESTS - successfulCalls);
    }

    @Test
    void driftCheck_does_not_touch_ledger() {
        UUID tenantId = seedTenant();
        when(platformLlmModelClient.call(any())).thenReturn(labelResult("{}"));

        ToolCallResult toolCallResult =
                underTenant(tenantId, () -> llmGateway.driftCheck("fixture"));

        assertThat(toolCallResult.action()).isEqualTo(Action.LABEL);
        verify(creditLedger, never()).reserve(any(), any());
        verify(creditLedger, never()).settle(any(ReservationId.class));
        verify(creditLedger, never()).release(any(ReservationId.class));
    }

    private UUID seedTenantWithCredits(int startingCredits) {
        UUID tenantId = seedTenant();
        underTenant(
                tenantId,
                () ->
                        creditLedgerEntryRepository.saveAndFlush(
                                CreditLedgerEntryEntity.topup(
                                        UUID.randomUUID(),
                                        tenantId,
                                        startingCredits,
                                        "SEPAY-SEED-" + tenantId)));
        clearInvocations(creditLedger);
        return tenantId;
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "llm-credit-" + tenantId);
        attachZeroAllowancePlan(tenantId);
        clearInvocations(creditLedger);
        return tenantId;
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

    private void seedByokCredentials(UUID tenantId) {
        byte[] encryptedEnvelope =
                refreshTokenCipher.encrypt(
                        "byok-key".getBytes(StandardCharsets.UTF_8), tenantId.toString());
        UserByokKeyEntity userByokKey =
                new UserByokKeyEntity(
                        tenantId,
                        UserByokKeyEntity.Provider.OPENAI,
                        "https://openrouter.ai/api/v1",
                        encryptedEnvelope,
                        null);
        userByokKey.recordConnectionTest(
                UserByokKeyEntity.LastTestResult.OK,
                java.time.Instant.parse("2026-05-20T00:00:00Z"),
                "[\"openai/gpt-5.4-nano\"]");
        userByokKey.selectModel("openai/gpt-5.4-nano");
        userByokKey.activate();
        underTenant(tenantId, () -> userByokKeyRepository.saveAndFlush(userByokKey));
        clearInvocations(creditLedger);
    }

    private static SanitizationContext sanitizedContext() {
        return new SanitizationContext("sanitized-user-message", 3, false, null);
    }

    private static LlmChatResult labelResult(String argumentsJson) {
        return new LlmChatResult(
                List.of(new RawToolCall("label", argumentsJson)), new LlmUsage(1, 1, "stop"));
    }

    private static LlmChatResult unsafeResult() {
        return new LlmChatResult(
                List.of(new RawToolCall("send", "{}")), new LlmUsage(1, 1, "stop"));
    }

    private void assertUsageAuditRow(
            UUID tenantId,
            String credentialSource,
            String callSite,
            int chargedCredits,
            int promptTokens,
            int completionTokens) {
        Map<String, Object> auditRow =
                jdbcTemplate.queryForMap(
                        """
                        SELECT credential_source, call_site, charged_credits, prompt_tokens, completion_tokens
                          FROM llm_call_audit
                         WHERE tenant_id = ?
                         ORDER BY created_at DESC
                         LIMIT 1
                        """,
                        tenantId);
        assertThat(auditRow.get("credential_source")).isEqualTo(credentialSource);
        assertThat(auditRow.get("call_site")).isEqualTo(callSite);
        assertThat(auditRow.get("charged_credits")).isEqualTo(chargedCredits);
        assertThat(auditRow.get("prompt_tokens")).isEqualTo(promptTokens);
        assertThat(auditRow.get("completion_tokens")).isEqualTo(completionTokens);
    }

    private static <T> T underTenant(UUID tenantId, TenantCallable<T> tenantCallable) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantCallable::call);
    }

    @FunctionalInterface
    private interface TenantCallable<T> {
        T call();
    }

    @TestConfiguration
    static class MeterRegistryTestConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
