package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class LlmGatewayByokRoutingTest extends PostgresContainerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000052");
    private static final byte[] PLAINTEXT_KEY =
            "sk-test-byok-secret".getBytes(StandardCharsets.UTF_8);

    @Autowired LlmGateway llmGateway;

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;

    @Autowired TenantByokCredentialsRepository tenantByokCredentialsRepository;

    @MockitoSpyBean RefreshTokenCipher refreshTokenCipher;

    @MockitoBean LlmModelClient platformLlmModelClient;

    @MockitoBean(name = "openAiByokModelClient")
    ByokLlmModelClient openAiByokModelClient;

    @MockitoBean(name = "anthropicByokModelClient")
    ByokLlmModelClient anthropicByokModelClient;

    @MockitoBean(name = "googleGenAiByokModelClient")
    ByokLlmModelClient googleGenAiByokModelClient;

    @MockitoBean(name = "deepSeekByokModelClient")
    ByokLlmModelClient deepSeekByokModelClient;

    @BeforeEach
    void resetRows() {
        jdbcTemplate.update("delete from credit_ledger_entry where tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("delete from credit_reservation where tenant_id = ?", TENANT_ID);
        // changelog 084/085 added llm_call_audit with FK to tenants — clear it before tenant delete
        jdbcTemplate.update("delete from llm_call_audit where tenant_id = ?", TENANT_ID);
        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .run(
                        () ->
                                tenantByokCredentialsRepository
                                        .findByTenantId(TENANT_ID)
                                        .ifPresent(tenantByokCredentialsRepository::delete));
        jdbcTemplate.update("delete from tenants where id = ?", TENANT_ID);
    }

    @Test
    void byok_row_routes_through_byok_client_not_platform() {
        seedByokTenant(TENANT_ID, BYOKProvider.ANTHROPIC, null, PLAINTEXT_KEY);
        when(anthropicByokModelClient.call(any(byte[].class), any(), any()))
                .thenReturn(labelResult("{}"));

        ToolCallResult toolCallResult =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

        assertThat(toolCallResult.action()).isEqualTo(Action.LABEL);
        verify(anthropicByokModelClient)
                .call(any(byte[].class), eq(null), any(LlmChatRequest.class));
        verify(platformLlmModelClient, never()).call(any());
    }

    @Test
    void no_byok_row_falls_through_to_platform() {
        seedTenantWithCredits(TENANT_ID, 5);
        when(platformLlmModelClient.call(any())).thenReturn(labelResult("{}"));

        ToolCallResult toolCallResult =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

        assertThat(toolCallResult.action()).isEqualTo(Action.LABEL);
        verify(platformLlmModelClient).call(any(LlmChatRequest.class));
        verify(anthropicByokModelClient, never()).call(any(byte[].class), anyString(), any());
        verify(openAiByokModelClient, never()).call(any(byte[].class), anyString(), any());
    }

    @Test
    void openai_byok_routes_to_openai_client() {
        String endpoint = "https://together.xyz/v1";
        seedByokTenant(TENANT_ID, BYOKProvider.OPENAI, endpoint, PLAINTEXT_KEY);
        when(openAiByokModelClient.call(any(byte[].class), anyString(), any()))
                .thenReturn(labelResult("{}"));

        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .run(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

        ArgumentCaptor<LlmChatRequest> requestCaptor =
                ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(openAiByokModelClient)
                .call(any(byte[].class), eq(endpoint), requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .satisfies(
                        request -> {
                            assertThat(request.model()).isEqualTo("openai/gpt-5.4-nano");
                            assertThat(request.toolChoiceRequired()).isTrue();
                            assertThat(request.temperature()).isZero();
                        });
        verify(platformLlmModelClient, never()).call(any());
    }

    @Test
    void google_genai_byok_routes_to_google_client() {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta";
        seedByokTenant(TENANT_ID, BYOKProvider.GOOGLE_GENAI, endpoint, PLAINTEXT_KEY);
        when(googleGenAiByokModelClient.call(any(byte[].class), anyString(), any()))
                .thenReturn(labelResult("{}"));

        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .run(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

        ArgumentCaptor<LlmChatRequest> requestCaptor =
                ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(googleGenAiByokModelClient)
                .call(any(byte[].class), eq(endpoint), requestCaptor.capture());
        assertThat(requestCaptor.getValue().model()).isEqualTo("gemini-2.0-flash");
        verify(platformLlmModelClient, never()).call(any());
    }

    @Test
    void deepseek_byok_routes_to_deepseek_client() {
        String endpoint = "https://api.deepseek.com";
        seedByokTenant(TENANT_ID, BYOKProvider.DEEPSEEK, endpoint, PLAINTEXT_KEY);
        when(deepSeekByokModelClient.call(any(byte[].class), anyString(), any()))
                .thenReturn(labelResult("{}"));

        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .run(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

        ArgumentCaptor<LlmChatRequest> requestCaptor =
                ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(deepSeekByokModelClient)
                .call(any(byte[].class), eq(endpoint), requestCaptor.capture());
        assertThat(requestCaptor.getValue().model()).isEqualTo("deepseek-chat");
        verify(platformLlmModelClient, never()).call(any());
    }

    @Test
    void cipher_decrypt_called_with_tenantId_aad() {
        byte[] encryptedEnvelope =
                seedByokTenant(TENANT_ID, BYOKProvider.ANTHROPIC, null, PLAINTEXT_KEY);
        AtomicReference<byte[]> copiedDecryptedKey = new AtomicReference<>();
        when(anthropicByokModelClient.call(any(byte[].class), any(), any()))
                .thenAnswer(
                        invocation -> {
                            byte[] decryptedKey = invocation.getArgument(0, byte[].class);
                            copiedDecryptedKey.set(
                                    Arrays.copyOf(decryptedKey, decryptedKey.length));
                            return labelResult("{}");
                        });

        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .run(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

        verify(refreshTokenCipher).decrypt(any(byte[].class), eq(TENANT_ID.toString()));
        verify(anthropicByokModelClient)
                .call(any(byte[].class), eq(null), any(LlmChatRequest.class));
        assertThat(copiedDecryptedKey.get()).containsExactly(PLAINTEXT_KEY);
        assertThat(encryptedEnvelope).isNotEmpty();
    }

    @Test
    void byok_path_does_not_log_key_bytes() {
        seedByokTenant(TENANT_ID, BYOKProvider.ANTHROPIC, null, PLAINTEXT_KEY);
        when(anthropicByokModelClient.call(any(byte[].class), any(), any()))
                .thenReturn(labelResult("{\"value\":\"Receipts\"}"));
        ch.qos.logback.classic.Logger gatewayLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LlmGatewayImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        gatewayLogger.addAppender(listAppender);

        try {
            ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                    .run(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));
        } finally {
            gatewayLogger.detachAppender(listAppender);
        }

        String formattedMessages =
                listAppender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .reduce(
                                "",
                                (combinedMessages, formattedMessage) ->
                                        combinedMessages + "\n" + formattedMessage);
        assertThat(formattedMessages)
                .contains("event=llm_byok_call_started tenantId=" + TENANT_ID)
                .contains("event=llm_byok_call_succeeded tenantId=" + TENANT_ID)
                .contains("provider=ANTHROPIC")
                .contains("model=claude-3-haiku-20240307")
                .doesNotContain("sk-test-byok-secret", "Receipts", "https://");
    }

    @Test
    void multitenant_no_key_leak() throws Exception {
        int requestCount = 16;
        List<UUID> tenantIds =
                IntStream.range(0, requestCount).mapToObj(_ -> UUID.randomUUID()).toList();
        for (int tenantIndex = 0; tenantIndex < requestCount; tenantIndex++) {
            UUID tenantId = tenantIds.get(tenantIndex);
            if (tenantIndex % 2 == 0) {
                seedByokTenant(
                        tenantId,
                        BYOKProvider.ANTHROPIC,
                        null,
                        ("sk-byok-" + tenantId).getBytes(StandardCharsets.UTF_8));
            } else {
                seedTenantWithCredits(tenantId, 5);
            }
        }
        ConcurrentHashMap<UUID, String> byokKeyByTenant = new ConcurrentHashMap<>();
        when(anthropicByokModelClient.call(any(byte[].class), any(), any()))
                .thenAnswer(
                        invocation -> {
                            UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
                            byte[] decryptedKey = invocation.getArgument(0, byte[].class);
                            byokKeyByTenant.put(
                                    tenantId, new String(decryptedKey, StandardCharsets.UTF_8));
                            return labelResult("{\"boundTenantId\":\"" + tenantId + "\"}");
                        });
        when(platformLlmModelClient.call(any()))
                .thenAnswer(
                        _ -> {
                            String tenantId = TenantContext.currentOrThrow();
                            return labelResult("{\"boundTenantId\":\"" + tenantId + "\"}");
                        });

        try (var scope = StructuredTaskScope.<ToolCallResult>open()) {
            var subtasks =
                    tenantIds.stream()
                            .map(
                                    tenantId ->
                                            scope.fork(
                                                    () ->
                                                            ScopedValue.where(
                                                                            TenantContext.TENANT,
                                                                            tenantId.toString())
                                                                    .call(
                                                                            () ->
                                                                                    llmGateway.chat(
                                                                                            CallSite
                                                                                                    .PREVIEW,
                                                                                            "hello"))))
                            .toList();
            scope.join();
            for (int tenantIndex = 0; tenantIndex < requestCount; tenantIndex++) {
                UUID tenantId = tenantIds.get(tenantIndex);
                assertThat(subtasks.get(tenantIndex).get().args().get("boundTenantId"))
                        .isEqualTo(tenantId.toString());
            }
        }
        for (int tenantIndex = 0; tenantIndex < requestCount; tenantIndex += 2) {
            UUID tenantId = tenantIds.get(tenantIndex);
            assertThat(byokKeyByTenant.get(tenantId)).isEqualTo("sk-byok-" + tenantId);
        }
    }

    private byte[] seedByokTenant(
            UUID tenantId, BYOKProvider provider, String endpoint, byte[] plaintextKey) {
        seedTenant(tenantId);
        byte[] encryptedEnvelope = refreshTokenCipher.encrypt(plaintextKey, tenantId.toString());
        TenantByokCredentialsEntity credentials =
                new TenantByokCredentialsEntity(
                        UUID.randomUUID(),
                        tenantId,
                        provider,
                        endpoint,
                        providerModel(provider),
                        encryptedEnvelope,
                        (short) 1);
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> tenantByokCredentialsRepository.saveAndFlush(credentials));
        return encryptedEnvelope;
    }

    private void seedTenant(UUID tenantId) {
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "tenant-" + tenantId);
    }

    private void seedTenantWithCredits(UUID tenantId, int startingCredits) {
        seedTenant(tenantId);
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                creditLedgerEntryRepository.saveAndFlush(
                                        CreditLedgerEntryEntity.topup(
                                                UUID.randomUUID(),
                                                tenantId,
                                                startingCredits,
                                                "SEPAY-BYOK-TEST-" + tenantId)));
    }

    private static String providerModel(BYOKProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> "claude-3-haiku-20240307";
            case DEEPSEEK -> "deepseek-chat";
            case GOOGLE_GENAI -> "gemini-2.0-flash";
            case OPENAI -> "openai/gpt-5.4-nano";
        };
    }

    private static LlmChatResult labelResult(String argumentsJson) {
        return new LlmChatResult(
                List.of(new RawToolCall("label", argumentsJson)), new LlmUsage(1, 1, "stop"));
    }
}
