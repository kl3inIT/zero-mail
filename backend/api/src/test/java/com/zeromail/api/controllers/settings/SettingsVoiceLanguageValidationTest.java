package com.zeromail.api.controllers.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class SettingsVoiceLanguageValidationTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanAssistantSettings() {
        jdbcTemplate.execute("TRUNCATE TABLE assistant_settings RESTART IDENTITY CASCADE");
    }

    @Test
    void voiceSettingsRejectUnsupportedOutputLanguage() throws Exception {
        Seed seed = seedUser("voice-language-invalid");

        ResponseEntity<String> response =
                client().put()
                        .uri("/api/settings/voice")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"aiOutputLanguage\":\"es\"}")
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.path("code").asString()).isEqualTo("error.validation");
        assertThat(json.path("fieldErrors")).isNotEmpty();
        assertThat(json.path("fieldErrors").get(0).path("field").asString())
                .isEqualTo("aiOutputLanguage");
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, label));
        UUID userId = UUID.randomUUID();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                users.save(
                                        new UserEntity(
                                                userId,
                                                tenantId,
                                                "sub-" + label,
                                                label + "@example.com")));
        return new Seed("sub-" + label, label + "@example.com");
    }

    private record Seed(String googleSubject, String email) {}
}
