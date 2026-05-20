package com.zeromail.api.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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

/**
 * Wave-0 invariant lock for the locale-resolution backend half. Plan 04 turned this GREEN by wiring
 * {@code MeController.me} to expose {@code preferredLanguage} and adding {@code PATCH
 * /me/language}. The companion {@code MeLanguageIntegrationTest} carries the cross-tenant +
 * sentinel coverage; this class keeps the original three Wave-0 invariants in their own focused
 * asserts so anyone reading the i18n test package sees the locked contract at a glance.
 *
 * <p>Drives a live Tomcat via {@code RestClient} so the test auth filter + {@code TenantContext}
 * ScopedValue binding run end-to-end (MockMvc's standalone setup skips servlet filters).
 *
 * <p>Sentinels: {@code "zz"} is the disallowed-locale marker. No raw user content, no refresh-token
 * shapes, no exception class names appear in any fixture.
 */
@ActiveProfiles("test")
@Import(TestSessionSupport.class)
@SuppressWarnings("SqlNoDataSourceInspection")
class LocaleResolutionTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired TestSessionSupport.TestSessionMinter minter;
    @Autowired JdbcTemplate jdbc;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private record Seed(UUID tenantId, UUID userId, String googleSubject, String email) {}

    private Seed seed(String label) {
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
        minter.mint("sub-" + label, label + "@example.com");
        return new Seed(tenantId, userId, "sub-" + label, label + "@example.com");
    }

    @Test
    @DisplayName("PATCH /me/language { language: 'en' } persists to users.preferred_language")
    void patch_me_language_persists_preferredLanguage_to_db() {
        Seed s = seed("locres-persist");

        ResponseEntity<String> res =
                client().patch()
                        .uri("/api/me/language")
                        .header(TestSessionSupport.HEADER_SUBJECT, s.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, s.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"language\":\"en\"}")
                        .retrieve()
                        .toEntity(String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);

        String dbValue =
                jdbc.queryForObject(
                        "SELECT preferred_language FROM users WHERE id = ?",
                        String.class,
                        s.userId());
        assertThat(dbValue).isEqualTo("en");
    }

    @Test
    @DisplayName("GET /me returns preferred_language from the DB row (default 'vi')")
    void get_me_returns_preferred_language() throws Exception {
        Seed s = seed("locres-get");

        ResponseEntity<String> res =
                client().get()
                        .uri("/api/me")
                        .header(TestSessionSupport.HEADER_SUBJECT, s.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, s.email())
                        .retrieve()
                        .toEntity(String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        JsonNode json = new ObjectMapper().readTree(res.getBody());
        assertThat(json.path("preferredLanguage").asString()).isEqualTo("vi");
    }

    @Test
    @DisplayName("PATCH /me/language { language: 'zz' } -> 400 with locked error contract")
    void patch_me_language_rejects_value_outside_allow_list() throws Exception {
        Seed s = seed("locres-reject");

        ResponseEntity<String> res =
                client().patch()
                        .uri("/api/me/language")
                        .header(TestSessionSupport.HEADER_SUBJECT, s.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, s.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"language\":\"zz\"}")
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::is4xxClientError,
                                (_, _) -> {
                                    /* swallow default error handler */
                                })
                        .toEntity(String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(String.valueOf(res.getHeaders().getContentType()))
                .startsWith("application/problem+json");

        JsonNode json = new ObjectMapper().readTree(res.getBody());
        assertThat(json.path("code").asString()).isEqualTo("error.validation");
        assertThat(json.path("fieldErrors").isArray()).isTrue();
        JsonNode firstFieldError = json.path("fieldErrors").get(0);
        assertThat(firstFieldError.path("field").asString()).isEqualTo("language");
        assertThat(firstFieldError.path("code").asString())
                .matches("error\\.validation\\.field\\.language\\..+");
    }
}
