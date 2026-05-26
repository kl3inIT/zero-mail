package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.sql.Timestamp;
import java.time.Instant;
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
class SenderSafetyNetDeleteAuthorityTest extends ApiPostgresTestBase {

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
    void userCreatedSafetyNetEntryCanBeDeleted() {
        Seed seed = seedUser("safety-delete-user");
        UUID protectedSenderId =
                insertProtectedSender(seed.tenantId(), "ceo@example.com", "EMAIL", true);

        ResponseEntity<Void> response = delete(seed, protectedSenderId).toBodilessEntity();

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from tenant_protected_sender_observation where id = ?",
                        Integer.class,
                        protectedSenderId);
        assertThat(rowCount).isZero();
    }

    @Test
    void observationCreatedSafetyNetEntryCannotBeDeletedByUser() throws Exception {
        Seed seed = seedUser("safety-delete-observed");
        UUID protectedSenderId =
                insertProtectedSender(seed.tenantId(), "observed@example.com", "EMAIL", false);

        ResponseEntity<String> response = delete(seed, protectedSenderId).toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.path("code").asString()).isEqualTo("safety_net.observation_not_deletable");
        assertThat(rowExists(protectedSenderId)).isTrue();
    }

    @Test
    void crossTenantDeleteReturns404AndKeepsEntry() {
        Seed owner = seedUser("safety-delete-owner");
        Seed otherTenant = seedUser("safety-delete-other");
        UUID protectedSenderId =
                insertProtectedSender(owner.tenantId(), "owner@example.com", "EMAIL", true);

        ResponseEntity<String> response =
                delete(otherTenant, protectedSenderId).toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(rowExists(protectedSenderId)).isTrue();
    }

    private RestClient.ResponseSpec delete(Seed seed, UUID protectedSenderId) {
        return client().delete()
                .uri("/api/triage/sender-safety-net/" + protectedSenderId)
                .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (_, _) -> {});
    }

    private UUID insertProtectedSender(
            UUID tenantId, String pattern, String patternKind, boolean createdByUser) {
        UUID protectedSenderId = UUID.randomUUID();
        Instant observedAt = Instant.now();
        jdbcTemplate.update(
                """
                insert into tenant_protected_sender_observation(
                  id, tenant_id, sender_email, first_observed_at, last_observed_at,
                  observation_count, pattern_kind, created_by_user, created_at, updated_at, version
                ) values (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, 0)
                """,
                protectedSenderId,
                tenantId,
                pattern,
                Timestamp.from(observedAt),
                Timestamp.from(observedAt),
                patternKind,
                createdByUser,
                Timestamp.from(observedAt),
                Timestamp.from(observedAt));
        return protectedSenderId;
    }

    private boolean rowExists(UUID protectedSenderId) {
        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from tenant_protected_sender_observation where id = ?",
                        Integer.class,
                        protectedSenderId);
        return rowCount != null && rowCount == 1;
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
