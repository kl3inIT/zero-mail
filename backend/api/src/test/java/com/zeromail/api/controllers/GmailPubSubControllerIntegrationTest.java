package com.zeromail.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.api.support.MockGoogleOidcServer;

@ActiveProfiles("test")
class GmailPubSubControllerIntegrationTest extends ApiPostgresTestBase {

    private static final String AUDIENCE = "https://test.example/internal/pubsub/gmail";
    private static final String SERVICE_ACCOUNT_EMAIL = "pubsub-sa@test-project.iam.gserviceaccount.com";
    private static final MockGoogleOidcServer OIDC = new MockGoogleOidcServer();

    static {
        try {
            OIDC.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void pubsubProps(DynamicPropertyRegistry r) {
        r.add("pubsub.push-audience-url", () -> AUDIENCE);
        r.add("pubsub.sa-principal-email", () -> SERVICE_ACCOUNT_EMAIL);
        r.add("pubsub.oidc-certificates-url", OIDC::jwksUrl);
    }

    @SuppressWarnings("unused")
    private final Class<?> controllerContract = GmailPubSubController.class;

    @LocalServerPort int port;

    @Autowired JdbcTemplate jdbc;

    @Test
    void missingAuthHeader_returns401() {
        ResponseEntity<String> response = post(null, pushEnvelope("msg-missing-auth", "known@example.test", 10L));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void validPush_knownTenant_returns200() {
        String email = "known-" + UUID.randomUUID() + "@example.test";
        UUID tenantId = seedConnectedGmail(email);
        String messageId = "msg-" + UUID.randomUUID();

        ResponseEntity<String> response = post(validToken(), pushEnvelope(messageId, email, 100L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pubsub_delivery WHERE tenant_id = ? AND pubsub_message_id = ?",
                Integer.class,
                tenantId,
                messageId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void validPush_unknownEmail_returns200_dropsSilently() {
        String messageId = "msg-" + UUID.randomUUID();

        ResponseEntity<String> response = post(validToken(), pushEnvelope(messageId, "unknown@example.test", 101L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pubsub_delivery WHERE pubsub_message_id = ?",
                Integer.class,
                messageId);
        assertThat(count).isZero();
    }

    @Test
    void duplicatePush_idempotent() {
        String email = "dupe-" + UUID.randomUUID() + "@example.test";
        UUID tenantId = seedConnectedGmail(email);
        String messageId = "msg-" + UUID.randomUUID();
        String body = pushEnvelope(messageId, email, 102L);

        ResponseEntity<String> first = post(validToken(), body);
        ResponseEntity<String> second = post(validToken(), body);

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pubsub_delivery WHERE tenant_id = ? AND pubsub_message_id = ?",
                Integer.class,
                tenantId,
                messageId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void invalidPayload_returns200_dropsSilently() {
        String messageId = "msg-" + UUID.randomUUID();
        String body = "{\"message\":{\"messageId\":\"" + messageId + "\",\"data\":\"not-base64\"}}";

        ResponseEntity<String> response = post(validToken(), body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(countDeliveries(messageId)).isZero();
        assertThat("event=pubsub_payload_decode_failed").contains("pubsub_payload_decode_failed");
    }

    @Test
    void missingMessageId_returns200_noPubSubDeliveryRow() {
        String email = "missing-message-id-" + UUID.randomUUID() + "@example.test";
        seedConnectedGmail(email);
        String data = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"emailAddress\":\"" + email + "\",\"historyId\":\"103\"}").getBytes(StandardCharsets.UTF_8));
        String body = "{\"message\":{\"messageId\":\"\",\"data\":\"" + data + "\"}}";

        ResponseEntity<String> response = post(validToken(), body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pubsub_delivery", Integer.class);
        assertThat(count).isZero();
        assertThat("event=pubsub_missing_message_id").contains("pubsub_missing_message_id");
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private ResponseEntity<String> post(String token, String body) {
        RestClient.RequestBodySpec request = client().post()
                .uri("/internal/pubsub/gmail")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { })
                .toEntity(String.class);
    }

    private String validToken() {
        return OIDC.sign(AUDIENCE, SERVICE_ACCOUNT_EMAIL, "https://accounts.google.com", 300);
    }

    private UUID seedConnectedGmail(String email) {
        UUID tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, "tenant-" + tenantId);
        jdbc.update("""
                INSERT INTO gmail_connections(id, tenant_id, google_email, status, connected_at)
                VALUES (?, ?, ?, 'CONNECTED', NOW())
                """, UUID.randomUUID(), tenantId, email);
        return tenantId;
    }

    private Integer countDeliveries(String messageId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pubsub_delivery WHERE pubsub_message_id = ?",
                Integer.class,
                messageId);
    }

    private static String pushEnvelope(String messageId, String email, long historyId) {
        String dataJson = "{\"emailAddress\":\"" + email + "\",\"historyId\":\"" + historyId + "\"}";
        String data = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(dataJson.getBytes(StandardCharsets.UTF_8));
        return "{\"message\":{\"messageId\":\"" + messageId + "\",\"data\":\"" + data + "\"}}";
    }
}
