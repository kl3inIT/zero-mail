package com.zeromail.api.controllers.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.usecases.CreditLedger;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.domain.ByokProviderPreset;
import com.zeromail.core.llm.exception.InvalidByokException;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.exception.SanitizationException;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import com.zeromail.core.llm.usecases.ByokService;
import com.zeromail.core.llm.usecases.ByokValidateCommand;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import({
    TestSessionSupport.class,
    ByokControllerIntegrationTest.ByokExceptionProbeController.class,
    ByokControllerIntegrationTest.ByokRestClientBuilderConfiguration.class
})
class ByokControllerIntegrationTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Autowired RestClient.Builder byokRestClientBuilder;

    @Autowired TenantRepository tenantRepository;

    @Autowired UserRepository userRepository;

    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;

    @Autowired ObjectMapper objectMapper;

    @Autowired RefreshTokenCipher refreshTokenCipher;

    @Autowired TenantByokCredentialsRepository tenantByokCredentialsRepository;

    @MockitoSpyBean ByokService byokService;

    private MockRestServiceServer mockRestServiceServer;

    @BeforeEach
    void setUpByokServer() {
        mockRestServiceServer = MockRestServiceServer.bindTo(byokRestClientBuilder).build();
    }

    @Test
    void post_validate_returns_200_for_valid_key() {
        Seed seed = seedUser("byok-validate-ok");
        mockRestServiceServer
                .expect(once(), requestTo("https://openrouter.ai/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                "{\"data\":[{\"id\":\"model-a\"}]}", MediaType.APPLICATION_JSON));

        ResponseEntity<String> response =
                client().post()
                        .uri("/api/llm/byok/validate")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                "{\"preset\":\"openrouter\","
                                        + "\"model\":\"model-a\","
                                        + "\"apiKey\":\"valid-key\"}")
                        .retrieve()
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode responseJson = objectMapper.readTree(response.getBody());
        assertThat(responseJson.path("ok").asBoolean()).isTrue();
        assertThat(responseJson.path("models").get(0).asString()).isEqualTo("model-a");
        mockRestServiceServer.verify();
    }

    @Test
    void post_save_returns_400_when_invalid_byok_exception_thrown() {
        Seed seed = seedUser("byok-save-invalid");

        ResponseEntity<String> response =
                client().post()
                        .uri("/api/llm/byok")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                "{\"preset\":\"anthropic-compatible\","
                                        + "\"endpoint\":\"https://example.com/v1\","
                                        + "\"model\":\"claude-3-haiku-20240307\","
                                        + "\"apiKey\":\"invalid-key\"}")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asString())
                .isEqualTo("error.llm.byok.invalid");
        assertThat(tenantByokCredentialsRepository.findByTenantId(seed.tenantId())).isEmpty();
        mockRestServiceServer.verify();
    }

    @Test
    void safety_violation_handler_returns_422() {
        JsonNode responseJson = postProbe("/test/llm/safety-violation");

        assertThat(responseJson.path("status").asInt()).isEqualTo(422);
        assertThat(responseJson.path("code").asString()).isEqualTo("error.llm.safety_violation");
    }

    @Test
    void sanitization_failed_handler_returns_500() {
        JsonNode responseJson = postProbe("/test/llm/sanitization-failed");

        assertThat(responseJson.path("status").asInt()).isEqualTo(500);
        assertThat(responseJson.path("code").asString()).isEqualTo("error.llm.sanitization_failed");
    }

    @Test
    void insufficient_credits_still_returns_402() {
        Seed seed = seedUser("byok-insufficient");

        ResponseEntity<String> response =
                client().post()
                        .uri("/test/llm/reserve-triage")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(402);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asString())
                .isEqualTo("error.billing.insufficient");
    }

    @Test
    void save_without_prior_validate_still_validates_server_side() {
        Seed seed = seedUser("byok-save-reprobe");
        mockRestServiceServer
                .expect(once(), requestTo("https://openrouter.ai/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ResponseEntity<String> response =
                client().post()
                        .uri("/api/llm/byok")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                "{\"preset\":\"openrouter\","
                                        + "\"model\":\"openai/gpt-5.4-nano\","
                                        + "\"apiKey\":\"revoked-key\"}")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asString())
                .isEqualTo("error.llm.byok.invalid");
        assertThat(tenantByokCredentialsRepository.findByTenantId(seed.tenantId())).isEmpty();
        mockRestServiceServer.verify();
    }

    @Test
    void post_validate_accepts_lowercase_preset_id() {
        Seed seed = seedUser("byok-lower-provider");
        mockRestServiceServer
                .expect(times(1), requestTo("https://openrouter.ai/api/v1/models"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        ResponseEntity<String> response =
                client().post()
                        .uri("/api/llm/byok/validate")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                "{\"preset\":\"openrouter\","
                                        + "\"model\":\"openai/gpt-5.4-nano\","
                                        + "\"apiKey\":\"k\"}")
                        .retrieve()
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<ByokValidateCommand> commandCaptor =
                ArgumentCaptor.forClass(ByokValidateCommand.class);
        verify(byokService).validate(eq(seed.tenantId()), commandCaptor.capture());
        assertThat(commandCaptor.getValue().preset()).isEqualTo(ByokProviderPreset.OPENROUTER);
        mockRestServiceServer.verify();

        ResponseEntity<String> uppercaseResponse =
                client().post()
                        .uri("/api/llm/byok/validate")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                "{\"preset\":\"OPENROUTER\","
                                        + "\"model\":\"openai/gpt-5.4-nano\","
                                        + "\"apiKey\":\"k\"}")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(uppercaseResponse.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void get_current_returns_lowercase_provider_id() {
        Seed seed = seedUser("byok-current-provider");
        byte[] encryptedEnvelope =
                refreshTokenCipher.encrypt(
                        "stored-key".getBytes(StandardCharsets.UTF_8), seed.tenantId().toString());
        ScopedValue.where(TenantContext.TENANT, seed.tenantId().toString())
                .run(
                        () ->
                                tenantByokCredentialsRepository.saveAndFlush(
                                        new TenantByokCredentialsEntity(
                                                UUID.randomUUID(),
                                                seed.tenantId(),
                                                BYOKProvider.ANTHROPIC,
                                                "https://api.anthropic.com/v1",
                                                "claude-3-haiku-20240307",
                                                encryptedEnvelope,
                                                (short) 1)));

        ResponseEntity<String> response =
                client().get()
                        .uri("/api/llm/byok")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.getBody()).path("provider").asString())
                .isEqualTo("anthropic");
    }

    private JsonNode postProbe(String uri) {
        ResponseEntity<String> response =
                client().post()
                        .uri(uri)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);
        return objectMapper.readTree(response.getBody());
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, label));
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

    private record Seed(UUID tenantId, String googleSubject, String email) {}

    @RestController
    static class ByokExceptionProbeController {

        private final CreditLedger creditLedger;

        ByokExceptionProbeController(CreditLedger creditLedger) {
            this.creditLedger = creditLedger;
        }

        @PostMapping("/test/llm/safety-violation")
        void safetyViolation() {
            throw new SafetyViolationException();
        }

        @PostMapping("/test/llm/sanitization-failed")
        void sanitizationFailed() {
            throw new SanitizationException(
                    "test-sanitizer", new RuntimeException("private cause"));
        }

        @PostMapping("/test/llm/invalid-byok")
        void invalidByok() {
            throw new InvalidByokException();
        }

        @PostMapping("/test/llm/reserve-triage")
        void reserveTriage() {
            UUID tenantId = TenantContext.currentTenantUuid();
            creditLedger.reserve(tenantId, CallSite.TRIAGE);
        }
    }

    @TestConfiguration
    static class ByokRestClientBuilderConfiguration {

        @Bean
        @Primary
        RestClient.Builder byokRestClientBuilder() {
            return RestClient.builder();
        }
    }
}
