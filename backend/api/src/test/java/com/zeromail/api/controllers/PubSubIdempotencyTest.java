package com.zeromail.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
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
class PubSubIdempotencyTest extends ApiPostgresTestBase {

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
        r.add("zeromail.gmail.pubsub.oidc-certificates-url", OIDC::jwksUrl);
    }

    @LocalServerPort int port;

    @Autowired JdbcTemplate jdbc;

    @Test
    void duplicatePushMessage_sameMessageId_onlyOnePubSubDeliveryRow() {
        String email = "dupe-" + UUID.randomUUID() + "@example.test";
        UUID tenantId = seedConnectedGmail(email);
        String messageId = UUID.randomUUID().toString();
        String body = pushEnvelope(messageId, email, 450L);

        ResponseEntity<String> first = post(body);
        ResponseEntity<String> second = post(body);

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(countRows(messageId)).as("UNIQUE(tenant_id, pubsub_message_id) dedup contract").isEqualTo(1);
        Integer tenantRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pubsub_delivery WHERE tenant_id = ? AND pubsub_message_id = ?",
                Integer.class,
                tenantId,
                messageId);
        assertThat(tenantRows).isEqualTo(1);
    }

    @Test
    void unknownEmailAddress_returns200_noPubSubDeliveryRow() {
        String messageId = UUID.randomUUID().toString();

        ResponseEntity<String> response = post(pushEnvelope(messageId, "unknown@example.test", 451L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(countRows(messageId)).isZero();
    }

    private ResponseEntity<String> post(String body) {
        return RestClient.create("http://localhost:" + port)
                .post()
                .uri("/internal/pubsub/gmail")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
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

    private static String pushEnvelope(String messageId, String email, long historyId) {
        String dataJson = "{\"emailAddress\":\"" + email + "\",\"historyId\":\"" + historyId + "\"}";
        String data = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(dataJson.getBytes(StandardCharsets.UTF_8));
        return "{\"message\":{\"messageId\":\"" + messageId + "\",\"data\":\"" + data + "\"}}";
    }

    private Integer countRows(String messageId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pubsub_delivery WHERE pubsub_message_id = ?",
                Integer.class,
                messageId);
    }
}
