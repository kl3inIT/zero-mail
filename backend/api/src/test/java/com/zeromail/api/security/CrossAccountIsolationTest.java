package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
import tools.jackson.databind.ObjectMapper;

/**
 * RED contract for AUD-06 and the Phase 10 CR-01 multi-row-primary shim gap.
 *
 * <p>Waits on the future active-mailbox HTTP surface and mailbox binding filter. The compile-green
 * probe mechanism is plain-string {@link RestClient} calls to planned endpoints such as {@code PUT
 * /api/gmail/active-mailbox/{gmailConnectionId}} and {@code GET /api/gmail/active-mailbox}; this
 * test does not import or name future controller/filter types. A tiny API-local raw-JDBC
 * two-mailbox fixture is used because {@code backend:api} does not compile against {@code
 * backend:core} test sources.
 */
@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class CrossAccountIsolationTest extends ApiPostgresTestBase {

    private static final String ACTIVE_MAILBOX_PATH =
            "/api/gmail/active-mailbox/{gmailConnectionId}";

    @LocalServerPort int serverPort;

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void activeMailboxSelectionReturnsNotFoundForForeignMailboxId() {
        SeedData requestingTenant = seedUser("cross-account-requesting");
        SeedData foreignTenant = seedUser("cross-account-foreign");
        UUID foreignMailboxId =
                insertMailbox(
                        foreignTenant.tenantId(),
                        "cross-account-foreign@example.test",
                        "CONNECTED",
                        true);

        ResponseEntity<String> response =
                putWithoutThrowing(
                        authenticatedClient(requestingTenant),
                        ACTIVE_MAILBOX_PATH,
                        foreignMailboxId);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(Objects.requireNonNullElse(response.getBody(), ""))
                .contains("error.gmail.mailbox.not_found");
    }

    @Test
    void activeMailboxSelectionReturnsConflictForDisconnectedOwnedMailbox() {
        SeedData seedData = seedUser("cross-account-disconnected");
        UUID disconnectedMailboxId =
                insertMailbox(
                        seedData.tenantId(),
                        "cross-account-disconnected@example.test",
                        "DISCONNECTED",
                        false);

        ResponseEntity<String> response =
                putWithoutThrowing(
                        authenticatedClient(seedData), ACTIVE_MAILBOX_PATH, disconnectedMailboxId);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(Objects.requireNonNullElse(response.getBody(), ""))
                .contains("error.gmail.disconnected");
    }

    @Test
    void inboxReadsResolveOnlyTheActiveMailbox() {
        SeedData seedData = seedUser("cross-account-inbox");
        SeededMailboxes seededMailboxes =
                seedConnectedMailboxes(seedData.tenantId(), "cross-account-inbox");
        RestClient restClient = authenticatedClient(seedData);

        ResponseEntity<String> activateSecondaryResponse =
                putWithoutThrowing(
                        restClient,
                        ACTIVE_MAILBOX_PATH,
                        seededMailboxes.secondaryGmailConnectionId());
        ResponseEntity<String> activeMailboxResponse =
                getWithoutThrowing(restClient, "/api/gmail/active-mailbox");
        ResponseEntity<String> inboxResponse =
                getWithoutThrowing(restClient, "/api/gmail/inbox?limit=20");

        assertThat(activateSecondaryResponse.getStatusCode().value()).isEqualTo(204);
        assertThat(activeMailboxResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(activeMailboxResponse.getBody())
                .contains(seededMailboxes.secondaryGmailConnectionId().toString());
        assertThat(inboxResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(Objects.requireNonNullElse(inboxResponse.getBody(), ""))
                .doesNotContain(seededMailboxes.primaryGmailConnectionId().toString());
    }

    @Test
    void confirmedOutboundSendUsesTheActiveExecutingMailbox() throws Exception {
        SeedData seedData = seedUser("cross-account-send");
        SeededMailboxes seededMailboxes =
                seedConnectedMailboxes(seedData.tenantId(), "cross-account-send");
        RestClient restClient = authenticatedClient(seedData);

        ResponseEntity<String> activateSecondaryResponse =
                putWithoutThrowing(
                        restClient,
                        ACTIVE_MAILBOX_PATH,
                        seededMailboxes.secondaryGmailConnectionId());
        ResponseEntity<String> confirmResponse =
                postJsonWithoutThrowing(
                        restClient,
                        "/api/chat/{chatId}/confirm",
                        UUID.randomUUID(),
                        Map.of(
                                "toolCallId",
                                "tool-send-active-mailbox",
                                "contentOverride",
                                Map.of("body", "User-authored mailbox routing smoke body"),
                                "vipAcknowledged",
                                true));

        assertThat(activateSecondaryResponse.getStatusCode().value()).isEqualTo(204);
        assertThat(confirmResponse.getStatusCode().value())
                .as(
                        "send confirmation must execute under the active mailbox binding, not tenant primary")
                .isEqualTo(200);
        assertThat(Objects.requireNonNullElse(confirmResponse.getBody(), ""))
                .contains(seededMailboxes.secondaryGmailConnectionId().toString())
                .doesNotContain(seededMailboxes.primaryGmailConnectionId().toString());
    }

    private ResponseEntity<String> putWithoutThrowing(
            RestClient restClient, String uriTemplate, UUID gmailConnectionId) {
        return restClient
                .put()
                .uri(uriTemplate, gmailConnectionId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, _) -> {})
                .toEntity(String.class);
    }

    private ResponseEntity<String> getWithoutThrowing(RestClient restClient, String uri) {
        return restClient
                .get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, _) -> {})
                .toEntity(String.class);
    }

    private ResponseEntity<String> postJsonWithoutThrowing(
            RestClient restClient, String uriTemplate, UUID pathId, Object body) throws Exception {
        return restClient
                .post()
                .uri(uriTemplate, pathId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, _) -> {})
                .toEntity(String.class);
    }

    private RestClient authenticatedClient(SeedData seedData) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .defaultHeader(TestSessionSupport.HEADER_SUBJECT, seedData.googleSubject())
                .defaultHeader(TestSessionSupport.HEADER_EMAIL, seedData.email())
                .build();
    }

    private SeedData seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, label));
        String uniqueSuffix = tenantId.toString();
        String googleSubject = "sub-" + label + "-" + uniqueSuffix;
        String email = label + "-" + uniqueSuffix + "@example.test";
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

    private SeededMailboxes seedConnectedMailboxes(UUID tenantId, String label) {
        String uniqueSuffix = tenantId.toString();
        UUID primaryGmailConnectionId =
                insertMailbox(
                        tenantId,
                        label + "+primary-" + uniqueSuffix + "@example.test",
                        "CONNECTED",
                        true);
        UUID secondaryGmailConnectionId =
                insertMailbox(
                        tenantId,
                        label + "+secondary-" + uniqueSuffix + "@example.test",
                        "CONNECTED",
                        false);
        return new SeededMailboxes(primaryGmailConnectionId, secondaryGmailConnectionId);
    }

    private UUID insertMailbox(
            UUID tenantId, String googleEmail, String connectionStatus, boolean primary) {
        UUID gmailConnectionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO gmail_connections(id, tenant_id, google_email, status, is_primary)
                VALUES (?, ?, ?, ?, ?)
                """,
                gmailConnectionId,
                tenantId,
                googleEmail,
                connectionStatus,
                primary);
        return gmailConnectionId;
    }

    private record SeedData(UUID tenantId, String googleSubject, String email) {}

    private record SeededMailboxes(
            UUID primaryGmailConnectionId, UUID secondaryGmailConnectionId) {}
}
