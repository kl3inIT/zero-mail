package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.net.URI;
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
class SenderSafetyNetDomainPatternTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanSafetyNetTables() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE tenant_protected_sender_observation, tenant_sender_opt_in RESTART IDENTITY CASCADE");
    }

    @Test
    void optInAcceptsDomainPatternAndPersistsDomainKind() throws Exception {
        Seed seed = seedUser("safety-domain");

        ResponseEntity<String> response = postOptIn(seed, "@acme.com");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.path("pattern").asString()).isEqualTo("@acme.com");
        assertThat(json.path("patternKind").asString()).isEqualTo("DOMAIN");
        assertThat(json.path("createdByUser").asBoolean()).isTrue();

        String patternKind =
                jdbcTemplate.queryForObject(
                        "select pattern_kind from tenant_protected_sender_observation where tenant_id = ? and sender_email = ?",
                        String.class,
                        seed.tenantId(),
                        "@acme.com");
        assertThat(patternKind).isEqualTo("DOMAIN");
    }

    @Test
    void optInAcceptsEmailPatternAndPersistsEmailKind() throws Exception {
        Seed seed = seedUser("safety-email");

        ResponseEntity<String> response = postOptIn(seed, "Ceo@Acme.com");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.path("pattern").asString()).isEqualTo("ceo@acme.com");
        assertThat(json.path("patternKind").asString()).isEqualTo("EMAIL");
        assertThat(json.path("createdByUser").asBoolean()).isTrue();
    }

    @Test
    void optInRejectsMalformedPattern() throws Exception {
        Seed seed = seedUser("safety-invalid");

        ResponseEntity<String> response = postOptIn(seed, "acme.com");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.path("code").asString()).isEqualTo("safety_net.pattern_invalid");
    }

    private ResponseEntity<String> postOptIn(Seed seed, String senderPattern) {
        return client().post()
                .uri(
                        URI.create(
                                "/api/triage/sender-safety-net/"
                                        + senderPattern.replace("@", "%40")
                                        + "/opt-in"))
                .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (_, _) -> {})
                .toEntity(String.class);
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
        return new Seed(tenantId, "sub-" + label, label + "@example.com");
    }

    private record Seed(UUID tenantId, String googleSubject, String email) {}
}
