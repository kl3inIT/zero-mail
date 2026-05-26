package com.zeromail.api.controllers.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class ByokControllerIntegrationTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Autowired TenantRepository tenantRepository;

    @Autowired UserRepository userRepository;

    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;

    @Autowired ObjectMapper objectMapper;

    @Test
    void legacyValidateEndpointReturnsGoneShim() throws Exception {
        Seed seed = seedUser("legacy-byok-validate");

        ResponseEntity<String> response =
                client().post()
                        .uri("/api/llm/byok/validate")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"provider\":\"OPENAI\"}")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertMoved(response);
    }

    @Test
    void legacySaveEndpointReturnsGoneShim() throws Exception {
        Seed seed = seedUser("legacy-byok-save");

        ResponseEntity<String> response =
                client().post()
                        .uri("/api/llm/byok")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"provider\":\"OPENAI\"}")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertMoved(response);
    }

    @Test
    void legacyCurrentEndpointReturnsGoneShim() throws Exception {
        Seed seed = seedUser("legacy-byok-current");

        ResponseEntity<String> response =
                client().get()
                        .uri("/api/llm/byok")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertMoved(response);
    }

    private void assertMoved(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(410);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/byok");
        JsonNode responseJson = objectMapper.readTree(response.getBody());
        assertThat(responseJson.path("code").asString()).isEqualTo("ai.byok.moved");
        assertThat(responseJson.path("message").asString()).isEqualTo("Use /api/byok instead");
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
}
