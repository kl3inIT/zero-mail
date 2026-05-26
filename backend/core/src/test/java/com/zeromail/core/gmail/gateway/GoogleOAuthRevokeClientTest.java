package com.zeromail.core.gmail.gateway;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * CASA Tier 2 V3.3.1 / V13.1.5 — Google OAuth refresh-token revocation contract.
 *
 * <p>The revoke client is best-effort: success (200) and already-invalid (400 invalid_token) both
 * return cleanly; any other failure must be swallowed so the caller can proceed to clear local
 * state, and the refresh-token value must never appear in any log line.
 */
class GoogleOAuthRevokeClientTest {

    private static final String SECRET_TOKEN = "1//revoke-secret-fixture";
    private static final String REVOKE_BASE_URL = "https://oauth2.googleapis.com";

    @Test
    void revoke_returns_cleanly_on_http_200() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(REVOKE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo(REVOKE_BASE_URL + "/revoke?token=" + SECRET_TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        GoogleOAuthRevokeClient googleOAuthRevokeClient =
                new GoogleOAuthRevokeClient(restClientBuilder.build());

        assertThatCode(() -> googleOAuthRevokeClient.revoke(SECRET_TOKEN))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void revoke_returns_cleanly_on_http_400_invalid_token() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(REVOKE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo(REVOKE_BASE_URL + "/revoke?token=" + SECRET_TOKEN))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"invalid_token\"}"));
        GoogleOAuthRevokeClient googleOAuthRevokeClient =
                new GoogleOAuthRevokeClient(restClientBuilder.build());

        assertThatCode(() -> googleOAuthRevokeClient.revoke(SECRET_TOKEN))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void revoke_swallows_http_500() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(REVOKE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo(REVOKE_BASE_URL + "/revoke?token=" + SECRET_TOKEN))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        GoogleOAuthRevokeClient googleOAuthRevokeClient =
                new GoogleOAuthRevokeClient(restClientBuilder.build());

        assertThatCode(() -> googleOAuthRevokeClient.revoke(SECRET_TOKEN))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void revoke_swallows_transport_failure() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(REVOKE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo(REVOKE_BASE_URL + "/revoke?token=" + SECRET_TOKEN))
                .andRespond(withException(new IOException("simulated network failure")));
        GoogleOAuthRevokeClient googleOAuthRevokeClient =
                new GoogleOAuthRevokeClient(restClientBuilder.build());

        assertThatCode(() -> googleOAuthRevokeClient.revoke(SECRET_TOKEN))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void revoke_skips_when_token_is_blank() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(REVOKE_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        // No expectations — server.verify() will pass only if no request was fired.
        GoogleOAuthRevokeClient googleOAuthRevokeClient =
                new GoogleOAuthRevokeClient(restClientBuilder.build());

        assertThatCode(() -> googleOAuthRevokeClient.revoke("")).doesNotThrowAnyException();
        assertThatCode(() -> googleOAuthRevokeClient.revoke(null)).doesNotThrowAnyException();
        server.verify();
    }
}
