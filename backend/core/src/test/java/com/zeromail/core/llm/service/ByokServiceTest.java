package com.zeromail.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmByokProperties;
import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.llm.model.ByokCurrent;
import com.zeromail.core.llm.model.ByokSaveCommand;
import com.zeromail.core.llm.model.ByokSaveResult;
import com.zeromail.core.llm.model.ByokValidateCommand;
import com.zeromail.core.llm.model.ByokValidateResult;
import com.zeromail.core.llm.model.InvalidByokException;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;

class ByokServiceTest extends PostgresContainerTest {

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired RefreshTokenCipher refreshTokenCipher;

  @Autowired TenantByokCredentialsRepository tenantByokCredentialsRepository;

  private MockRestServiceServer mockRestServiceServer;
  private ByokService byokService;

  @BeforeEach
  void setUp() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    mockRestServiceServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    byokService = newService(restClientBuilder);
  }

  @Test
  void validate_openai_compatible_calls_models_endpoint() {
    UUID tenantId = seedTenant();
    mockRestServiceServer
        .expect(once(), requestTo("https://together.xyz/v1/models"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer test-key"))
        .andRespond(
            withSuccess(
                "{\"data\":[{\"id\":\"model-a\"},{\"id\":\"model-b\"}]}",
                MediaType.APPLICATION_JSON));

    ByokValidateResult result =
        underTenant(
            tenantId,
            () ->
                byokService.validate(
                    tenantId,
                    new ByokValidateCommand(
                        BYOKProvider.OPENAI_COMPATIBLE, "https://together.xyz/v1", "test-key")));

    assertThat(result.ok()).isTrue();
    assertThat(result.models()).containsExactly("model-a", "model-b");
    assertThat(result.reason()).isNull();
    mockRestServiceServer.verify();
  }

  @Test
  void validate_openai_compatible_failure() {
    UUID tenantId = seedTenant();
    mockRestServiceServer
        .expect(once(), requestTo("https://openrouter.ai/api/v1/models"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("upstream body must stay private"));

    ByokValidateResult result =
        underTenant(
            tenantId,
            () ->
                byokService.validate(
                    tenantId,
                    new ByokValidateCommand(
                        BYOKProvider.OPENAI_COMPATIBLE,
                        "https://openrouter.ai/api/v1",
                        "revoked-key")));

    assertThat(result.ok()).isFalse();
    assertThat(result.models()).isNull();
    assertThat(result.reason()).isEqualTo("upstream_rejected");
    assertThat(result.reason()).doesNotContain("upstream body");
    mockRestServiceServer.verify();
  }

  @Test
  void validate_anthropic_calls_messages_endpoint() {
    UUID tenantId = seedTenant();
    mockRestServiceServer
        .expect(once(), requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-api-key", "anthropic-key"))
        .andExpect(header("anthropic-version", "2023-06-01"))
        .andExpect(jsonPath("$.max_tokens").value(1))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    ByokValidateResult result =
        underTenant(
            tenantId,
            () ->
                byokService.validate(
                    tenantId,
                    new ByokValidateCommand(
                        BYOKProvider.ANTHROPIC, "https://api.anthropic.com/v1", "anthropic-key")));

    assertThat(result.ok()).isTrue();
    assertThat(result.models()).isNull();
    assertThat(result.reason()).isNull();
    mockRestServiceServer.verify();
  }

  @Test
  void openrouter_validate_does_not_double_prefix_v1() {
    UUID tenantId = seedTenant();
    mockRestServiceServer
        .expect(once(), requestTo("https://openrouter.ai/api/v1/models"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

    underTenant(
        tenantId,
        () ->
            byokService.validate(
                tenantId,
                new ByokValidateCommand(
                    BYOKProvider.OPENAI_COMPATIBLE,
                    "https://openrouter.ai/api/v1",
                    "openrouter-key")));

    mockRestServiceServer.verify();
  }

  @Test
  void openai_validate_uses_v1_models() {
    UUID tenantId = seedTenant();
    mockRestServiceServer
        .expect(once(), requestTo("https://api.openai.com/v1/models"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

    underTenant(
        tenantId,
        () ->
            byokService.validate(
                tenantId,
                new ByokValidateCommand(
                    BYOKProvider.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "openai-key")));

    mockRestServiceServer.verify();
  }

  @Test
  void trailing_slash_does_not_change_outbound_url() {
    UUID tenantId = seedTenant();
    mockRestServiceServer
        .expect(once(), requestTo("https://openrouter.ai/api/v1/models"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

    underTenant(
        tenantId,
        () ->
            byokService.validate(
                tenantId,
                new ByokValidateCommand(
                    BYOKProvider.OPENAI_COMPATIBLE,
                    "https://openrouter.ai/api/v1/",
                    "openrouter-key")));

    mockRestServiceServer.verify();
  }

  @Test
  void save_encrypts_key_via_refresh_token_cipher() {
    UUID tenantId = seedTenant();
    mockSuccessfulOpenAiProbe("https://openrouter.ai/api/v1/models");

    ByokSaveResult result =
        underTenant(
            tenantId,
            () ->
                byokService.save(
                    tenantId,
                    new ByokSaveCommand(
                        BYOKProvider.OPENAI_COMPATIBLE,
                        "https://openrouter.ai/api/v1",
                        "plaintext-key")));

    assertThat(result.ok()).isTrue();
    TenantByokCredentialsEntity credentials = findCredentials(tenantId);
    assertThat(credentials.getEncryptedKey())
        .isNotEqualTo("plaintext-key".getBytes(StandardCharsets.UTF_8));
    byte[] decryptedKey =
        refreshTokenCipher.decrypt(credentials.getEncryptedKey(), tenantId.toString());
    assertThat(new String(decryptedKey, StandardCharsets.UTF_8)).isEqualTo("plaintext-key");
    mockRestServiceServer.verify();
  }

  @Test
  void save_upserts_existing_row() {
    UUID tenantId = seedTenant();
    byte[] firstEnvelope =
        refreshTokenCipher.encrypt("old-key".getBytes(StandardCharsets.UTF_8), tenantId.toString());
    underTenant(
        tenantId,
        () ->
            tenantByokCredentialsRepository.saveAndFlush(
                new TenantByokCredentialsEntity(
                    UUID.randomUUID(),
                    tenantId,
                    BYOKProvider.ANTHROPIC,
                    "https://api.anthropic.com/v1",
                    firstEnvelope,
                    (short) 1)));
    mockSuccessfulOpenAiProbe("https://openrouter.ai/api/v1/models");

    underTenant(
        tenantId,
        () ->
            byokService.save(
                tenantId,
                new ByokSaveCommand(
                    BYOKProvider.OPENAI_COMPATIBLE,
                    "https://openrouter.ai/api/v1",
                    "replacement-key")));

    List<TenantByokCredentialsEntity> allCredentials =
        underTenant(
            tenantId,
            () ->
                tenantByokCredentialsRepository.findAll().stream()
                    .filter(credentials -> credentials.getTenantId().equals(tenantId))
                    .toList());
    assertThat(allCredentials).hasSize(1);
    assertThat(allCredentials.getFirst().getProvider()).isEqualTo(BYOKProvider.OPENAI_COMPATIBLE);
    assertThat(allCredentials.getFirst().getEndpoint()).isEqualTo("https://openrouter.ai/api/v1");
    byte[] decryptedKey =
        refreshTokenCipher.decrypt(
            allCredentials.getFirst().getEncryptedKey(), tenantId.toString());
    assertThat(new String(decryptedKey, StandardCharsets.UTF_8)).isEqualTo("replacement-key");
    mockRestServiceServer.verify();
  }

  @Test
  void current_returns_metadata_only() {
    UUID tenantId = seedTenant();
    byte[] encryptedEnvelope =
        refreshTokenCipher.encrypt(
            "stored-key".getBytes(StandardCharsets.UTF_8), tenantId.toString());
    underTenant(
        tenantId,
        () ->
            tenantByokCredentialsRepository.saveAndFlush(
                new TenantByokCredentialsEntity(
                    UUID.randomUUID(),
                    tenantId,
                    BYOKProvider.OPENAI_COMPATIBLE,
                    "https://together.xyz/v1?query-never-stored",
                    encryptedEnvelope,
                    (short) 1)));

    ByokCurrent current = underTenant(tenantId, () -> byokService.current(tenantId).orElseThrow());

    assertThat(current.provider()).isEqualTo(BYOKProvider.OPENAI_COMPATIBLE);
    assertThat(current.endpointHost()).isEqualTo("together.xyz");
    assertThat(current.savedAt()).isNotNull();
    assertThat(current.toString()).doesNotContain("stored-key", "encryptedKey", "v1?query");
  }

  @Test
  void anthropic_save_rejects_metadata_endpoint() {
    UUID tenantId = seedTenant();

    assertThatThrownBy(
            () ->
                underTenant(
                    tenantId,
                    () ->
                        byokService.save(
                            tenantId,
                            new ByokSaveCommand(
                                BYOKProvider.ANTHROPIC,
                                "http://169.254.169.254/v1",
                                "anthropic-key"))))
        .isInstanceOf(InvalidByokException.class);
    assertThat(tenantByokCredentialsRepository.findByTenantId(tenantId)).isEmpty();
    mockRestServiceServer.verify();
  }

  @Test
  void anthropic_save_rejects_rfc1918_endpoint() {
    UUID tenantId = seedTenant();

    assertThatThrownBy(
            () ->
                underTenant(
                    tenantId,
                    () ->
                        byokService.save(
                            tenantId,
                            new ByokSaveCommand(
                                BYOKProvider.ANTHROPIC, "http://10.0.0.5/v1", "anthropic-key"))))
        .isInstanceOf(InvalidByokException.class);
    assertThat(tenantByokCredentialsRepository.findByTenantId(tenantId)).isEmpty();
    mockRestServiceServer.verify();
  }

  @Test
  void anthropic_save_rejects_non_anthropic_host() {
    UUID tenantId = seedTenant();

    assertThatThrownBy(
            () ->
                underTenant(
                    tenantId,
                    () ->
                        byokService.save(
                            tenantId,
                            new ByokSaveCommand(
                                BYOKProvider.ANTHROPIC,
                                "https://example.com/v1",
                                "anthropic-key"))))
        .isInstanceOf(InvalidByokException.class);
    assertThat(tenantByokCredentialsRepository.findByTenantId(tenantId)).isEmpty();
    mockRestServiceServer.verify();
  }

  @Test
  void openai_compat_accepts_when_operator_opt_in() {
    UUID tenantId = seedTenant();
    mockSuccessfulOpenAiProbe("https://together.xyz/v1/models");

    ByokSaveResult result =
        underTenant(
            tenantId,
            () ->
                byokService.save(
                    tenantId,
                    new ByokSaveCommand(
                        BYOKProvider.OPENAI_COMPATIBLE, "https://together.xyz/v1", "test-key")));

    assertThat(result.ok()).isTrue();
    assertThat(findCredentials(tenantId).getEndpoint()).isEqualTo("https://together.xyz/v1");
    mockRestServiceServer.verify();
  }

  @Test
  void save_re_runs_upstream_probe() {
    UUID tenantId = seedTenant();
    mockRestServiceServer
        .expect(times(1), requestTo("https://openrouter.ai/api/v1/models"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
    mockRestServiceServer
        .expect(times(1), requestTo("https://openrouter.ai/api/v1/models"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    ByokValidateResult validateResult =
        underTenant(
            tenantId,
            () ->
                byokService.validate(
                    tenantId,
                    new ByokValidateCommand(
                        BYOKProvider.OPENAI_COMPATIBLE,
                        "https://openrouter.ai/api/v1",
                        "soon-revoked-key")));

    assertThat(validateResult.ok()).isTrue();
    assertThatThrownBy(
            () ->
                underTenant(
                    tenantId,
                    () ->
                        byokService.save(
                            tenantId,
                            new ByokSaveCommand(
                                BYOKProvider.OPENAI_COMPATIBLE,
                                "https://openrouter.ai/api/v1",
                                "soon-revoked-key"))))
        .isInstanceOf(InvalidByokException.class);
    assertThat(tenantByokCredentialsRepository.findByTenantId(tenantId)).isEmpty();
    mockRestServiceServer.verify();
  }

  @Test
  void byok_service_has_no_api_dto_parameters() {
    assertThat(ByokService.class.getDeclaredMethods())
        .allSatisfy(
            methodUnderTest ->
                assertThat(methodUnderTest.getParameterTypes())
                    .allSatisfy(
                        parameterType ->
                            assertThat(parameterType.getPackageName())
                                .doesNotStartWith("com.zeromail.api.dto")));
  }

  private ByokService newService(RestClient.Builder restClientBuilder) {
    return new ByokService(
        tenantByokCredentialsRepository,
        refreshTokenCipher,
        new ByokEndpointValidator(
            new ZeroMailLlmByokProperties(
                true, List.of(), Duration.ofSeconds(5), Duration.ofSeconds(15))),
        restClientBuilder);
  }

  private void mockSuccessfulOpenAiProbe(String expectedUrl) {
    mockRestServiceServer
        .expect(once(), requestTo(expectedUrl))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"data\":[{\"id\":\"model-a\"}]}", MediaType.APPLICATION_JSON));
  }

  private TenantByokCredentialsEntity findCredentials(UUID tenantId) {
    return underTenant(
        tenantId, () -> tenantByokCredentialsRepository.findByTenantId(tenantId).orElseThrow());
  }

  private UUID seedTenant() {
    UUID tenantId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into tenants(id, display_name) values (?, ?)", tenantId, "tenant-" + tenantId);
    return tenantId;
  }

  private static <T> T underTenant(UUID tenantId, TenantCallable<T> tenantCallable) {
    return ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(tenantCallable::call);
  }

  @FunctionalInterface
  private interface TenantCallable<T> {
    T call();
  }
}
