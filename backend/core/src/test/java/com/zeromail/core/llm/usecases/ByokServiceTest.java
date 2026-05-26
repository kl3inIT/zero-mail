package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmByokProperties;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.domain.ByokProviderPreset;
import com.zeromail.core.llm.exception.InvalidByokException;
import com.zeromail.core.llm.gateway.springai.ByokValidationGateway;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
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

class ByokServiceTest extends PostgresContainerTest {

    private static final String OPENROUTER_MODEL = "anthropic/claude-3.5-sonnet";
    private static final String ANTHROPIC_MODEL = "claude-3-haiku-20240307";
    private static final String GOOGLE_GENAI_MODEL = "gemini-2.0-flash";
    private static final String DEEPSEEK_MODEL = "deepseek-chat";

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired RefreshTokenCipher refreshTokenCipher;

    @Autowired TenantByokCredentialsRepository tenantByokCredentialsRepository;

    private MockRestServiceServer mockRestServiceServer;
    private RestClient.Builder restClientBuilder;
    private ByokService byokService;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
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
                                                ByokProviderPreset.OPENAI_COMPATIBLE,
                                                "https://together.xyz/v1",
                                                "model-a",
                                                "test-key")));

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
                .andRespond(
                        withStatus(HttpStatus.UNAUTHORIZED)
                                .body("upstream body must stay private"));

        ByokValidateResult result =
                underTenant(
                        tenantId,
                        () ->
                                byokService.validate(
                                        tenantId,
                                        new ByokValidateCommand(
                                                ByokProviderPreset.OPENROUTER,
                                                null,
                                                OPENROUTER_MODEL,
                                                "revoked-key")));

        assertThat(result.ok()).isFalse();
        assertThat(result.models()).isNull();
        assertThat(result.reason()).isEqualTo("upstream_rejected");
        assertThat(result.reason()).doesNotContain("upstream body");
        mockRestServiceServer.verify();
    }

    @Test
    void validate_returns_model_not_found_when_models_endpoint_excludes_selection() {
        UUID tenantId = seedTenant();
        mockRestServiceServer
                .expect(once(), requestTo("https://api.openai.com/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                "{\"data\":[{\"id\":\"gpt-5.4-nano\"},{\"id\":\"gpt-4.1-mini\"}]}",
                                MediaType.APPLICATION_JSON));

        ByokValidateResult result =
                underTenant(
                        tenantId,
                        () ->
                                byokService.validate(
                                        tenantId,
                                        new ByokValidateCommand(
                                                ByokProviderPreset.OPENAI,
                                                null,
                                                "missing-model",
                                                "openai-key")));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).isEqualTo("model_not_found");
        assertThat(result.models()).containsExactly("gpt-5.4-nano", "gpt-4.1-mini");
        mockRestServiceServer.verify();
    }

    @Test
    void validate_returns_endpoint_rejected_for_missing_custom_endpoint() {
        UUID tenantId = seedTenant();

        ByokValidateResult result =
                underTenant(
                        tenantId,
                        () ->
                                byokService.validate(
                                        tenantId,
                                        new ByokValidateCommand(
                                                ByokProviderPreset.ANTHROPIC_COMPATIBLE,
                                                " ",
                                                ANTHROPIC_MODEL,
                                                "key")));

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).isEqualTo("endpoint_rejected");
        mockRestServiceServer.verify();
    }

    @Test
    void validate_anthropic_calls_models_endpoint() {
        UUID tenantId = seedTenant();
        mockRestServiceServer
                .expect(once(), requestTo("https://api.anthropic.com/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "anthropic-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(
                        withSuccess(
                                "{\"data\":[{\"id\":\"" + ANTHROPIC_MODEL + "\"}]}",
                                MediaType.APPLICATION_JSON));

        ByokValidateResult result =
                underTenant(
                        tenantId,
                        () ->
                                byokService.validate(
                                        tenantId,
                                        new ByokValidateCommand(
                                                ByokProviderPreset.ANTHROPIC,
                                                null,
                                                ANTHROPIC_MODEL,
                                                "anthropic-key")));

        assertThat(result.ok()).isTrue();
        assertThat(result.models()).containsExactly(ANTHROPIC_MODEL);
        assertThat(result.reason()).isNull();
        mockRestServiceServer.verify();
    }

    @Test
    void validate_google_genai_calls_models_endpoint() {
        UUID tenantId = seedTenant();
        mockRestServiceServer
                .expect(
                        once(),
                        requestTo("https://generativelanguage.googleapis.com/v1beta/models"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-goog-api-key", "google-key"))
                .andRespond(
                        withSuccess(
                                """
                {"models":[
                  {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]},
                  {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}
                ]}
                """,
                                MediaType.APPLICATION_JSON));

        ByokValidateResult result =
                underTenant(
                        tenantId,
                        () ->
                                byokService.validate(
                                        tenantId,
                                        new ByokValidateCommand(
                                                ByokProviderPreset.GOOGLE_GENAI,
                                                null,
                                                GOOGLE_GENAI_MODEL,
                                                "google-key")));

        assertThat(result.ok()).isTrue();
        assertThat(result.models()).containsExactly(GOOGLE_GENAI_MODEL);
        assertThat(result.reason()).isNull();
        mockRestServiceServer.verify();
    }

    @Test
    void validate_deepseek_calls_models_endpoint() {
        UUID tenantId = seedTenant();
        mockRestServiceServer
                .expect(once(), requestTo("https://api.deepseek.com/models"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer deepseek-key"))
                .andRespond(
                        withSuccess(
                                "{\"data\":[{\"id\":\"deepseek-chat\"},{\"id\":\"deepseek-reasoner\"}]}",
                                MediaType.APPLICATION_JSON));

        ByokValidateResult result =
                underTenant(
                        tenantId,
                        () ->
                                byokService.validate(
                                        tenantId,
                                        new ByokValidateCommand(
                                                ByokProviderPreset.DEEPSEEK,
                                                null,
                                                DEEPSEEK_MODEL,
                                                "deepseek-key")));

        assertThat(result.ok()).isTrue();
        assertThat(result.models()).containsExactly("deepseek-chat", "deepseek-reasoner");
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
                                        ByokProviderPreset.OPENROUTER,
                                        null,
                                        OPENROUTER_MODEL,
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
                                        ByokProviderPreset.OPENAI,
                                        null,
                                        "gpt-5.4-nano",
                                        "openai-key")));

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
                                        ByokProviderPreset.OPENAI_COMPATIBLE,
                                        "https://openrouter.ai/api/v1/",
                                        OPENROUTER_MODEL,
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
                                                ByokProviderPreset.OPENROUTER,
                                                null,
                                                OPENROUTER_MODEL,
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
                refreshTokenCipher.encrypt(
                        "old-key".getBytes(StandardCharsets.UTF_8), tenantId.toString());
        underTenant(
                tenantId,
                () ->
                        tenantByokCredentialsRepository.saveAndFlush(
                                new TenantByokCredentialsEntity(
                                        UUID.randomUUID(),
                                        tenantId,
                                        BYOKProvider.ANTHROPIC,
                                        "https://api.anthropic.com/v1",
                                        ANTHROPIC_MODEL,
                                        firstEnvelope,
                                        (short) 1)));
        mockSuccessfulOpenAiProbe("https://openrouter.ai/api/v1/models");

        underTenant(
                tenantId,
                () ->
                        byokService.save(
                                tenantId,
                                new ByokSaveCommand(
                                        ByokProviderPreset.OPENROUTER,
                                        null,
                                        OPENROUTER_MODEL,
                                        "replacement-key")));

        List<TenantByokCredentialsEntity> allCredentials =
                underTenant(
                        tenantId,
                        () ->
                                tenantByokCredentialsRepository.findAll().stream()
                                        .filter(
                                                credentials ->
                                                        credentials.getTenantId().equals(tenantId))
                                        .toList());
        assertThat(allCredentials).hasSize(1);
        assertThat(allCredentials.getFirst().getProvider()).isEqualTo(BYOKProvider.OPENAI);
        assertThat(allCredentials.getFirst().getEndpoint())
                .isEqualTo("https://openrouter.ai/api/v1");
        assertThat(allCredentials.getFirst().getModel()).isEqualTo(OPENROUTER_MODEL);
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
                                        BYOKProvider.OPENAI,
                                        "https://together.xyz/v1?query-never-stored",
                                        OPENROUTER_MODEL,
                                        encryptedEnvelope,
                                        (short) 1)));

        ByokCurrent current =
                underTenant(tenantId, () -> byokService.current(tenantId).orElseThrow());

        assertThat(current.provider()).isEqualTo(BYOKProvider.OPENAI);
        assertThat(current.endpointHost()).isEqualTo("together.xyz");
        assertThat(current.model()).isEqualTo(OPENROUTER_MODEL);
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
                                                                ByokProviderPreset
                                                                        .ANTHROPIC_COMPATIBLE,
                                                                "http://169.254.169.254/v1",
                                                                ANTHROPIC_MODEL,
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
                                                                ByokProviderPreset
                                                                        .ANTHROPIC_COMPATIBLE,
                                                                "http://10.0.0.5/v1",
                                                                ANTHROPIC_MODEL,
                                                                "anthropic-key"))))
                .isInstanceOf(InvalidByokException.class);
        assertThat(tenantByokCredentialsRepository.findByTenantId(tenantId)).isEmpty();
        mockRestServiceServer.verify();
    }

    @Test
    void anthropic_save_rejects_non_anthropic_host() {
        UUID tenantId = seedTenant();
        byokService = newService(restClientBuilder, false);

        assertThatThrownBy(
                        () ->
                                underTenant(
                                        tenantId,
                                        () ->
                                                byokService.save(
                                                        tenantId,
                                                        new ByokSaveCommand(
                                                                ByokProviderPreset
                                                                        .ANTHROPIC_COMPATIBLE,
                                                                "https://example.com/v1",
                                                                ANTHROPIC_MODEL,
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
                                                ByokProviderPreset.OPENAI_COMPATIBLE,
                                                "https://together.xyz/v1",
                                                "model-a",
                                                "test-key")));

        assertThat(result.ok()).isTrue();
        assertThat(findCredentials(tenantId).getEndpoint()).isEqualTo("https://together.xyz/v1");
        mockRestServiceServer.verify();
    }

    @Test
    void anthropic_compat_accepts_when_operator_opt_in() {
        UUID tenantId = seedTenant();
        mockRestServiceServer
                .expect(once(), requestTo("https://example.com/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "anthropic-key"))
                .andRespond(
                        withSuccess(
                                "{\"data\":[{\"id\":\"" + ANTHROPIC_MODEL + "\"}]}",
                                MediaType.APPLICATION_JSON));

        ByokSaveResult result =
                underTenant(
                        tenantId,
                        () ->
                                byokService.save(
                                        tenantId,
                                        new ByokSaveCommand(
                                                ByokProviderPreset.ANTHROPIC_COMPATIBLE,
                                                "https://example.com/v1",
                                                ANTHROPIC_MODEL,
                                                "anthropic-key")));

        assertThat(result.ok()).isTrue();
        TenantByokCredentialsEntity credentials = findCredentials(tenantId);
        assertThat(credentials.getProvider()).isEqualTo(BYOKProvider.ANTHROPIC);
        assertThat(credentials.getEndpoint()).isEqualTo("https://example.com/v1");
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
                                                ByokProviderPreset.OPENROUTER,
                                                null,
                                                OPENROUTER_MODEL,
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
                                                                ByokProviderPreset.OPENROUTER,
                                                                null,
                                                                OPENROUTER_MODEL,
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
                                                                .doesNotStartWith(
                                                                        "com.zeromail.api.dto")));
    }

    private ByokService newService(RestClient.Builder restClientBuilder) {
        return newService(restClientBuilder, true);
    }

    private ByokService newService(
            RestClient.Builder restClientBuilder, boolean allowNonVendorEndpoints) {
        return new ByokService(
                tenantByokCredentialsRepository,
                refreshTokenCipher,
                new ByokEndpointValidator(
                        new ZeroMailLlmByokProperties(
                                allowNonVendorEndpoints,
                                allowNonVendorEndpoints
                                        ? List.of("together.xyz", "example.com")
                                        : List.of(),
                                List.of(),
                                Duration.ofSeconds(5),
                                Duration.ofSeconds(15))),
                new ByokValidationGateway(restClientBuilder));
    }

    private void mockSuccessfulOpenAiProbe(String expectedUrl) {
        mockRestServiceServer
                .expect(once(), requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                "{\"data\":[{\"id\":\""
                                        + OPENROUTER_MODEL
                                        + "\"},{\"id\":\"model-a\"}]}",
                                MediaType.APPLICATION_JSON));
    }

    private TenantByokCredentialsEntity findCredentials(UUID tenantId) {
        return underTenant(
                tenantId,
                () -> tenantByokCredentialsRepository.findByTenantId(tenantId).orElseThrow());
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        return tenantId;
    }

    private static <T> T underTenant(UUID tenantId, TenantCallable<T> tenantCallable) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantCallable::call);
    }

    @FunctionalInterface
    private interface TenantCallable<T> {
        T call();
    }
}
