package com.zeromail.api.controllers.account;

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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class MeControllerTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("GET /api/me includes triagePaused from tenant state")
    void me_response_contains_triagePaused_field() {
        Seed seed = seedUser("me-triage-paused", true, false);
        String raw = getMe(seed);

        assertThat(raw).contains("\"triagePaused\":true");
    }

    @Test
    @DisplayName("GET /api/me includes Gmail status with ingestionHealth")
    void me_response_contains_gmailConnectionStatus_with_ingestionHealth() {
        Seed seed = seedUser("me-gmail-health", true, true);
        String raw = getMe(seed);

        assertThat(raw).contains("\"gmailConnectionStatus\"");
        assertThat(raw).contains("\"status\":\"CONNECTED\"");
        assertThat(raw).contains("\"ingestionHealth\"");
        assertThat(raw).contains("\"ingestionHealth\":\"WATCH_UNHEALTHY\"");
    }

    @Test
    @DisplayName("GET /api/me serializes extended shape cleanly")
    void me_response_json_shape_serializes_cleanly() {
        Seed seed = seedUser("me-json-shape", false, false);
        String raw = getMe(seed);

        assertThat(raw)
                .contains("\"triagePaused\":false")
                .contains("\"gmailConnectionStatus\"")
                .contains("\"ingestionHealth\":\"HEALTHY\"");
    }

    @Test
    @DisplayName("GET /api/me prefers Google session identity for email, name, and picture")
    void me_response_prefers_google_session_identity() {
        Seed seed = seedUser("me-google-identity", false, false);

        String raw =
                getMe(
                        seed,
                        "fresh-google-user@example.com",
                        "Google Profile Name",
                        "https://lh3.googleusercontent.com/a/profile-photo");

        assertThat(raw)
                .contains("\"email\":\"fresh-google-user@example.com\"")
                .contains("\"displayName\":\"Google Profile Name\"")
                .contains("\"avatarUrl\":\"https://lh3.googleusercontent.com/a/profile-photo\"");
    }

    @Test
    @DisplayName("GET /api/me without test auth returns 401")
    void me_missingTestAuth_returns401() {
        ResponseEntity<String> response =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/api/me")
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    private String getMe(Seed seed) {
        return getMe(seed, seed.email(), null, null);
    }

    private String getMe(
            Seed seed, String sessionEmail, String sessionName, String sessionPicture) {
        RestClient.RequestHeadersSpec<?> request =
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/api/me")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, sessionEmail);
        if (sessionName != null) {
            request = request.header(TestSessionSupport.HEADER_NAME, sessionName);
        }
        if (sessionPicture != null) {
            request = request.header(TestSessionSupport.HEADER_PICTURE, sessionPicture);
        }
        return request.retrieve().body(String.class);
    }

    private Seed seedUser(String label, boolean triagePaused, boolean unhealthyGmail) {
        UUID tenantId = UUID.randomUUID();
        TenantEntity tenant = new TenantEntity(tenantId, label);
        tenant.setTriagePaused(triagePaused);
        tenants.save(tenant);
        UUID userId = UUID.randomUUID();
        String subject = "sub-" + label;
        String email = label + "@example.com";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> users.save(new UserEntity(userId, tenantId, subject, email)));
        if (unhealthyGmail) {
            jdbc.update(
                    """
                    INSERT INTO gmail_connections(id, tenant_id, google_email, status, connected_at, ingestion_health)
                    VALUES (?, ?, ?, 'CONNECTED', NOW(), 'WATCH_UNHEALTHY')
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    email);
        }
        return new Seed(tenantId, subject, email);
    }

    private record Seed(UUID tenantId, String googleSubject, String email) {}
}
