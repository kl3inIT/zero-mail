package com.zeromail.api.controllers.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class MailboxOwnershipSeamTest extends ApiPostgresTestBase {

    @LocalServerPort int serverPort;

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void setPrimary_returns_404_for_missing_or_not_owned_mailbox() throws Exception {
        SeedData seedData = seedUser("mailbox-ownership-missing");
        SeedData otherSeedData = seedUser("mailbox-ownership-other");
        UUID otherTenantMailboxId =
                insertMailbox(
                        otherSeedData.tenantId(), "other-owned@example.test", "CONNECTED", true);

        JsonNode missingProblem =
                postProblem(
                        authenticatedClient(seedData),
                        "/api/gmail/mailboxes/{gmailConnectionId}/set-primary",
                        UUID.randomUUID());
        JsonNode notOwnedProblem =
                postProblem(
                        authenticatedClient(seedData),
                        "/api/gmail/mailboxes/{gmailConnectionId}/set-primary",
                        otherTenantMailboxId);

        assertThat(missingProblem.path("status").asInt()).isEqualTo(404);
        assertThat(notOwnedProblem.path("status").asInt()).isEqualTo(404);
        assertThat(missingProblem.path("code").asString())
                .isEqualTo("error.gmail.mailbox.not_found");
        assertThat(notOwnedProblem.path("code").asString())
                .isEqualTo("error.gmail.mailbox.not_found");
    }

    @Test
    void setPrimary_returns_409_for_any_non_connected_owned_mailbox() throws Exception {
        SeedData seedData = seedUser("mailbox-ownership-status");
        UUID disconnectedMailboxId =
                insertMailbox(
                        seedData.tenantId(), "disconnected@example.test", "DISCONNECTED", false);
        UUID pendingMailboxId =
                insertMailbox(seedData.tenantId(), "pending@example.test", "PENDING", false);
        UUID notConnectedMailboxId =
                insertMailbox(
                        seedData.tenantId(), "not-connected@example.test", "NOT_CONNECTED", false);

        assertDisconnectedProblem(
                postProblem(authenticatedClient(seedData), disconnectedMailboxId));
        assertDisconnectedProblem(postProblem(authenticatedClient(seedData), pendingMailboxId));
        assertDisconnectedProblem(
                postProblem(authenticatedClient(seedData), notConnectedMailboxId));
    }

    @Test
    void reconnect_is_reachable_for_disconnected_owned_mailbox_but_not_for_not_owned_mailbox() {
        SeedData seedData = seedUser("mailbox-ownership-reconnect");
        SeedData otherSeedData = seedUser("mailbox-ownership-reconnect-other");
        UUID disconnectedMailboxId =
                insertMailbox(
                        seedData.tenantId(), "reconnect-owned@example.test", "DISCONNECTED", false);
        UUID otherTenantMailboxId =
                insertMailbox(
                        otherSeedData.tenantId(),
                        "reconnect-other@example.test",
                        "DISCONNECTED",
                        false);

        ResponseEntity<String> reachableResponse =
                authenticatedClient(seedData)
                        .post()
                        .uri(
                                "/api/gmail/mailboxes/{gmailConnectionId}/reconnect",
                                disconnectedMailboxId)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);
        ResponseEntity<String> notOwnedResponse =
                authenticatedClient(seedData)
                        .post()
                        .uri(
                                "/api/gmail/mailboxes/{gmailConnectionId}/reconnect",
                                otherTenantMailboxId)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(reachableResponse.getStatusCode().value()).isEqualTo(302);
        assertThat(reachableResponse.getHeaders().getLocation())
                .isEqualTo(URI.create("/oauth2/authorization/google?reconnect=true"));
        assertThat(notOwnedResponse.getStatusCode().value()).isEqualTo(404);
    }

    private JsonNode postProblem(RestClient restClient, UUID gmailConnectionId) throws Exception {
        return postProblem(
                restClient,
                "/api/gmail/mailboxes/{gmailConnectionId}/set-primary",
                gmailConnectionId);
    }

    private JsonNode postProblem(RestClient restClient, String uri, UUID gmailConnectionId)
            throws Exception {
        ResponseEntity<String> response =
                restClient
                        .post()
                        .uri(uri, gmailConnectionId)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);
        assertThat(response.getStatusCode().isError()).isTrue();
        return objectMapper.readTree(response.getBody());
    }

    private void assertDisconnectedProblem(JsonNode problemJson) {
        assertThat(problemJson.path("status").asInt()).isEqualTo(409);
        assertThat(problemJson.path("code").asString())
                .as(
                        "resolveOwnedConnectionOrThrow must fail closed on DISCONNECTED/PENDING/NOT_CONNECTED")
                .isEqualTo("error.gmail.disconnected");
    }

    private RestClient authenticatedClient(SeedData seedData) {
        HttpClient noRedirectHttpClient =
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .requestFactory(new JdkClientHttpRequestFactory(noRedirectHttpClient))
                .defaultHeader(TestSessionSupport.HEADER_SUBJECT, seedData.googleSubject())
                .defaultHeader(TestSessionSupport.HEADER_EMAIL, seedData.email())
                .build();
    }

    private SeedData seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, label));
        String googleSubject = "sub-" + label + "-" + UUID.randomUUID();
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
        return new SeedData(tenantId, googleSubject, email);
    }

    private UUID insertMailbox(
            UUID tenantId, String googleEmail, String connectionStatus, boolean primary) {
        UUID gmailConnectionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into gmail_connections(id, tenant_id, google_email, status, is_primary)
                values (?, ?, ?, ?, ?)
                """,
                gmailConnectionId,
                tenantId,
                googleEmail,
                connectionStatus,
                primary);
        return gmailConnectionId;
    }

    private record SeedData(UUID tenantId, String googleSubject, String email) {}
}
