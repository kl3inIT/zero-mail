package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.LlmModelClient;
import com.zeromail.core.llm.usecases.LlmProviderChatExecutor;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
import com.zeromail.core.llm.usecases.LlmUsage;
import com.zeromail.core.llm.usecases.RawToolCall;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ByokResolutionIntegrationTest extends PostgresContainerTest {

    @Autowired LlmGateway llmGateway;

    @Autowired UserByokKeyRepository userByokKeyRepository;

    @Autowired RefreshTokenCipher refreshTokenCipher;

    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;

    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean LlmProviderChatExecutor providerChatExecutor;

    @MockitoBean LlmModelClient platformLlmModelClient;

    @Test
    void activeTestedByokRowOverridesPlatformDefault() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000009424");
        seedTenant(tenantId);
        UserByokKeyEntity userByokKey = seedActiveByokKey(tenantId);
        when(providerChatExecutor.call(any(LlmProviderCredential.class), any(LlmChatRequest.class)))
                .thenReturn(labelResult("{}"));
        when(platformLlmModelClient.call(any(LlmChatRequest.class))).thenReturn(labelResult("{}"));

        underTenant(tenantId, () -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

        ArgumentCaptor<LlmProviderCredential> credentialCaptor =
                ArgumentCaptor.forClass(LlmProviderCredential.class);
        ArgumentCaptor<LlmChatRequest> requestCaptor =
                ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(providerChatExecutor).call(credentialCaptor.capture(), requestCaptor.capture());
        assertThat(credentialCaptor.getValue().baseUrl()).isEqualTo("https://byok.example.test/v1");
        assertThat(requestCaptor.getValue().model()).isEqualTo("gpt-4o-mini");
        verify(platformLlmModelClient, never()).call(any(LlmChatRequest.class));

        clearInvocations(providerChatExecutor, platformLlmModelClient);
        userByokKey.deactivate();
        userByokKeyRepository.saveAndFlush(userByokKey);
        seedTenantWithCredits(tenantId, 3);

        underTenant(tenantId, () -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

        verify(platformLlmModelClient).call(any(LlmChatRequest.class));
        verify(providerChatExecutor, never()).call(any(), any());
    }

    private UserByokKeyEntity seedActiveByokKey(UUID tenantId) {
        byte[] encryptedKey =
                refreshTokenCipher.encrypt(
                        "sk-active-byok".getBytes(StandardCharsets.UTF_8), tenantId.toString());
        UserByokKeyEntity userByokKey =
                new UserByokKeyEntity(
                        tenantId,
                        UserByokKeyEntity.Provider.OPENAI,
                        "https://byok.example.test/v1",
                        encryptedKey,
                        null);
        userByokKey.recordConnectionTest(
                UserByokKeyEntity.LastTestResult.OK,
                Instant.parse("2026-05-20T00:00:00Z"),
                "[\"gpt-4o-mini\"]");
        userByokKey.selectModel("gpt-4o-mini");
        userByokKey.activate();
        return userByokKeyRepository.saveAndFlush(userByokKey);
    }

    private void seedTenant(UUID tenantId) {
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?) on conflict (id) do nothing",
                tenantId,
                "byok-resolution-test");
    }

    private void seedTenantWithCredits(UUID tenantId, int credits) {
        underTenant(
                tenantId,
                () ->
                        creditLedgerEntryRepository.saveAndFlush(
                                CreditLedgerEntryEntity.topup(
                                        UUID.randomUUID(),
                                        tenantId,
                                        credits,
                                        "SEPAY-BYOK-RESOLUTION-" + tenantId)));
    }

    private static void underTenant(UUID tenantId, Runnable runnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(runnable);
    }

    private static LlmChatResult labelResult(String argumentsJson) {
        return new LlmChatResult(
                List.of(new RawToolCall("label", argumentsJson)), new LlmUsage(1, 1, "stop"));
    }
}
