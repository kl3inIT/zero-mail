package com.zeromail.api.controllers.tenant;

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

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class TriagePauseControllerTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("PUT /api/tenant/triage-pause with paused=true persists triage_paused")
    void putTriagePause_true_persists_triage_paused() {
        RestClient client = RestClient.create("http://localhost:" + port);
        Seed seed = seedUser("triage-pause-true");

        String raw =
                client.put()
                        .uri("/api/tenant/triage-pause")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"paused\":true}")
                        .retrieve()
                        .body(String.class);

        assertThat(raw).contains("\"paused\":true");
        assertThat(triagePaused(seed.tenantId())).isTrue();
    }

    @Test
    @DisplayName("PUT /api/tenant/triage-pause with paused=false clears triage_paused")
    void putTriagePause_false_clears_triage_paused() {
        RestClient client = RestClient.create("http://localhost:" + port);
        Seed seed = seedUser("triage-pause-false");
        jdbc.update("UPDATE tenants SET triage_paused = true WHERE id = ?", seed.tenantId());

        String raw =
                client.put()
                        .uri("/api/tenant/triage-pause")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"paused\":false}")
                        .retrieve()
                        .body(String.class);

        assertThat(raw).contains("\"paused\":false");
        assertThat(triagePaused(seed.tenantId())).isFalse();
    }

    @Test
    @DisplayName("PUT /api/tenant/triage-pause without test auth returns 401")
    void putTriagePause_missingTestAuth_returns401() {
        RestClient client = RestClient.create("http://localhost:" + port);

        ResponseEntity<String> res =
                client.put()
                        .uri("/api/tenant/triage-pause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"paused\":true}")
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    private Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, label));
        UUID userId = UUID.randomUUID();
        String subject = "sub-" + label;
        String email = label + "@example.com";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> users.save(new UserEntity(userId, tenantId, subject, email)));
        return new Seed(tenantId, subject, email);
    }

    private Boolean triagePaused(UUID tenantId) {
        return jdbc.queryForObject(
                "SELECT triage_paused FROM tenants WHERE id = ?", Boolean.class, tenantId);
    }

    private record Seed(UUID tenantId, String googleSubject, String email) {}
}
