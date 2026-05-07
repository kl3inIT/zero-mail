package com.zeromail.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.model.Action;
import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.llm.model.LlmChatRequest;
import com.zeromail.core.llm.model.LlmChatResult;
import com.zeromail.core.llm.model.LlmUsage;
import com.zeromail.core.llm.model.RawToolCall;
import com.zeromail.core.llm.model.ToolCallResult;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;

class LlmGatewayByokRoutingTest extends PostgresContainerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000052");
  private static final byte[] PLAINTEXT_KEY = "sk-test-byok-secret".getBytes(StandardCharsets.UTF_8);

  @Autowired LlmGateway llmGateway;

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired TenantByokCredentialsRepository tenantByokCredentialsRepository;

  @MockitoSpyBean RefreshTokenCipher refreshTokenCipher;

  @MockitoBean LlmModelClient platformLlmModelClient;

  @MockitoBean(name = "openAiCompatibleByokModelClient")
  ByokLlmModelClient openAiCompatibleByokModelClient;

  @MockitoBean(name = "anthropicByokModelClient")
  ByokLlmModelClient anthropicByokModelClient;

  @BeforeEach
  void resetRows() {
    jdbcTemplate.update("delete from tenant_byok_credentials");
    jdbcTemplate.update("delete from tenants");
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
    verify(anthropicByokModelClient).call(any(byte[].class), eq(null), any(LlmChatRequest.class));
    verify(platformLlmModelClient, never()).call(any());
  }

  @Test
  void no_byok_row_falls_through_to_platform() {
    seedTenant(TENANT_ID);
    when(platformLlmModelClient.call(any())).thenReturn(labelResult("{}"));

    ToolCallResult toolCallResult =
        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
            .call(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

    assertThat(toolCallResult.action()).isEqualTo(Action.LABEL);
    verify(platformLlmModelClient).call(any(LlmChatRequest.class));
    verify(anthropicByokModelClient, never()).call(any(byte[].class), anyString(), any());
    verify(openAiCompatibleByokModelClient, never()).call(any(byte[].class), anyString(), any());
  }

  @Test
  void openai_compat_byok_uses_mutate_seam() {
    String endpoint = "https://together.xyz/v1";
    seedByokTenant(TENANT_ID, BYOKProvider.OPENAI_COMPATIBLE, endpoint, PLAINTEXT_KEY);
    when(openAiCompatibleByokModelClient.call(any(byte[].class), anyString(), any()))
        .thenReturn(labelResult("{}"));

    ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
        .run(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

    ArgumentCaptor<LlmChatRequest> requestCaptor = ArgumentCaptor.forClass(LlmChatRequest.class);
    verify(openAiCompatibleByokModelClient)
        .call(any(byte[].class), eq(endpoint), requestCaptor.capture());
    assertThat(requestCaptor.getValue())
        .satisfies(
            request -> {
              assertThat(request.model()).isEqualTo("openai/gpt-4o-mini");
              assertThat(request.toolChoiceRequired()).isTrue();
              assertThat(request.temperature()).isZero();
            });
    verify(platformLlmModelClient, never()).call(any());
  }

  @Test
  void cipher_decrypt_called_with_tenantId_aad() {
    byte[] encryptedEnvelope = seedByokTenant(TENANT_ID, BYOKProvider.ANTHROPIC, null, PLAINTEXT_KEY);
    AtomicReference<byte[]> copiedDecryptedKey = new AtomicReference<>();
    when(anthropicByokModelClient.call(any(byte[].class), any(), any()))
        .thenAnswer(
            invocation -> {
              byte[] decryptedKey = invocation.getArgument(0, byte[].class);
              copiedDecryptedKey.set(Arrays.copyOf(decryptedKey, decryptedKey.length));
              return labelResult("{}");
            });

    ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
        .run(() -> llmGateway.chat(CallSite.PREVIEW, "<p>hello</p>"));

    verify(refreshTokenCipher).decrypt(any(byte[].class), eq(TENANT_ID.toString()));
    verify(anthropicByokModelClient).call(any(byte[].class), eq(null), any(LlmChatRequest.class));
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
            .reduce("", (combinedMessages, formattedMessage) -> combinedMessages + "\n" + formattedMessage);
    assertThat(formattedMessages)
        .contains("event=llm_byok_call_started tenantId=" + TENANT_ID)
        .contains("event=llm_byok_call_succeeded tenantId=" + TENANT_ID)
        .contains("provider=ANTHROPIC")
        .contains("model=openai/gpt-4o-mini")
        .doesNotContain("sk-test-byok-secret", "Receipts", "https://");
  }

  @Test
  void multitenant_no_key_leak() throws Exception {
    int requestCount = 100;
    List<UUID> tenantIds = IntStream.range(0, requestCount).mapToObj(_ -> UUID.randomUUID()).toList();
    for (int tenantIndex = 0; tenantIndex < requestCount; tenantIndex++) {
      UUID tenantId = tenantIds.get(tenantIndex);
      if (tenantIndex % 2 == 0) {
        seedByokTenant(
            tenantId,
            BYOKProvider.ANTHROPIC,
            null,
            ("sk-byok-" + tenantId).getBytes(StandardCharsets.UTF_8));
      } else {
        seedTenant(tenantId);
      }
    }
    ConcurrentHashMap<UUID, String> byokKeyByTenant = new ConcurrentHashMap<>();
    when(anthropicByokModelClient.call(any(byte[].class), any(), any()))
        .thenAnswer(
            invocation -> {
              UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
              byte[] decryptedKey = invocation.getArgument(0, byte[].class);
              byokKeyByTenant.put(tenantId, new String(decryptedKey, StandardCharsets.UTF_8));
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
                              ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                  .call(() -> llmGateway.chat(CallSite.PREVIEW, "hello"))))
              .toList();
      scope.join();
      for (int tenantIndex = 0; tenantIndex < requestCount; tenantIndex++) {
        UUID tenantId = tenantIds.get(tenantIndex);
        assertThat(subtasks.get(tenantIndex).get().args().get("boundTenantId"))
            .isEqualTo(tenantId.toString());
      }
    }
    tenantIds.stream()
        .filter(tenantId -> tenantIds.indexOf(tenantId) % 2 == 0)
        .forEach(
            tenantId ->
                assertThat(byokKeyByTenant.get(tenantId)).isEqualTo("sk-byok-" + tenantId));
  }

  private byte[] seedByokTenant(
      UUID tenantId, BYOKProvider provider, String endpoint, byte[] plaintextKey) {
    seedTenant(tenantId);
    byte[] encryptedEnvelope = refreshTokenCipher.encrypt(plaintextKey, tenantId.toString());
    TenantByokCredentialsEntity credentials =
        new TenantByokCredentialsEntity(
            UUID.randomUUID(), tenantId, provider, endpoint, encryptedEnvelope, (short) 1);
    ScopedValue.where(TenantContext.TENANT, tenantId.toString())
        .run(() -> tenantByokCredentialsRepository.saveAndFlush(credentials));
    return encryptedEnvelope;
  }

  private void seedTenant(UUID tenantId) {
    jdbcTemplate.update("insert into tenants(id, display_name) values (?, ?)", tenantId, "tenant-" + tenantId);
  }

  private static LlmChatResult labelResult(String argumentsJson) {
    return new LlmChatResult(
        List.of(new RawToolCall("label", argumentsJson)), new LlmUsage(1, 1, "stop"));
  }
}
