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
class KnowledgeSnippetControllerTenantIsolationTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanKnowledgeSnippets() {
        jdbcTemplate.execute("TRUNCATE TABLE assistant_knowledge_snippet RESTART IDENTITY CASCADE");
    }

    @Test
    void deleteForAnotherTenantReturns404AndKeepsSnippet() throws Exception {
        Seed tenantA = seedUser("knowledge-tenant-a");
        Seed tenantB = seedUser("knowledge-tenant-b");
        UUID snippetId = createSnippet(tenantA, "Tenant A title", "Tenant A content");

        ResponseEntity<String> crossTenantDelete =
                client().delete()
                        .uri("/api/knowledge-snippets/" + snippetId)
                        .header(TestSessionSupport.HEADER_SUBJECT, tenantB.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, tenantB.email())
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(crossTenantDelete.getStatusCode().value()).isEqualTo(404);
        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from assistant_knowledge_snippet where id = ?",
                        Integer.class,
                        snippetId);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void duplicateTenantTitleReturnsKnowledgeDuplicateCode() throws Exception {
        Seed tenant = seedUser("knowledge-duplicate");
        createSnippet(tenant, "Duplicate title", "first content");

        ResponseEntity<String> duplicateCreate =
                client().post()
                        .uri("/api/knowledge-snippets")
                        .header(TestSessionSupport.HEADER_SUBJECT, tenant.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, tenant.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"title\":\"Duplicate title\",\"content\":\"second content\"}")
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(duplicateCreate.getStatusCode().value()).isEqualTo(409);
        JsonNode json = objectMapper.readTree(duplicateCreate.getBody());
        assertThat(json.path("code").asString()).isEqualTo("knowledge.title.duplicate");
    }

    private UUID createSnippet(Seed seed, String title, String content) throws Exception {
        ResponseEntity<String> createResponse =
                client().post()
                        .uri("/api/knowledge-snippets")
                        .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                        .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}")
                        .retrieve()
                        .toEntity(String.class);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        return UUID.fromString(
                objectMapper.readTree(createResponse.getBody()).path("id").asString());
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
