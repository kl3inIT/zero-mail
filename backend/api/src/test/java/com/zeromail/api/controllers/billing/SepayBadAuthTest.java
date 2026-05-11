package com.zeromail.api.controllers.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.dto.billing.SepayWebhookPayload;
import com.zeromail.api.support.ApiPostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@ActiveProfiles("test")
class SepayBadAuthTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void missing_authorization_header_returns_401() {
        ResponseEntity<String> response = postWebhook(null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(countLedgerEntries()).isZero();
    }

    @Test
    void wrong_prefix_returns_401() {
        ResponseEntity<String> response = postWebhook("Bearer abc");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(countLedgerEntries()).isZero();
    }

    @Test
    void wrong_apikey_returns_401() {
        ResponseEntity<String> response = postWebhook("Apikey wrong-secret");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(countLedgerEntries()).isZero();
    }

    private ResponseEntity<String> postWebhook(String authorizationHeader) {
        RestClient.RequestBodySpec request =
                RestClient.create("http://localhost:" + port)
                        .post()
                        .uri("/api/billing/sepay/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload());
        if (authorizationHeader != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return request.retrieve()
                .onStatus(HttpStatusCode::isError, (_, _) -> {})
                .toEntity(String.class);
    }

    private Long countLedgerEntries() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM credit_ledger_entry", Long.class);
    }

    private static SepayWebhookPayload payload() {
        return new SepayWebhookPayload(
                999L,
                "VCB",
                "2026-05-05 12:00:00",
                "0123",
                "ABC12345",
                "ABC12345 nap tien zeromail",
                "in",
                100_000L,
                0L,
                null,
                "ABC12345",
                "bank sms");
    }
}
