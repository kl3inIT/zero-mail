package com.zeromail.core.gmail.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Best-effort caller of Google's OAuth2 token revocation endpoint ({@code
 * https://oauth2.googleapis.com/revoke}). Invoked on Gmail disconnect so the persisted refresh
 * token cannot be silently reused if the encrypted ciphertext were ever to leak.
 *
 * <p>Best-effort contract: success states (HTTP 200, or HTTP 400 with body containing {@code
 * invalid_token}) return cleanly; any other error is logged and swallowed so the caller can proceed
 * to clear local DB state regardless of Google's availability.
 *
 * <p>Privacy: the {@code refresh_token} value is NEVER logged or echoed into exception messages.
 * Log lines carry only {@code event} + {@code statusCode} / {@code reason}.
 *
 * <p>Closes CASA Tier 2 V3.3.1 (Logout invalidates session/token state), V9.2.2 (Outbound TLS use
 * of authenticated endpoints), V13.1.5 (Token revocation on user action).
 */
@Component
public class GoogleOAuthRevokeClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthRevokeClient.class);
    private static final String REVOKE_BASE_URL = "https://oauth2.googleapis.com";
    private static final String INVALID_TOKEN_MARKER = "invalid_token";

    private final RestClient revokeRestClient;

    public GoogleOAuthRevokeClient() {
        this(RestClient.builder().baseUrl(REVOKE_BASE_URL).build());
    }

    GoogleOAuthRevokeClient(RestClient revokeRestClient) {
        this.revokeRestClient = revokeRestClient;
    }

    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            revokeRestClient
                    .post()
                    .uri(
                            uriBuilder ->
                                    uriBuilder
                                            .path("/revoke")
                                            .queryParam("token", refreshToken)
                                            .build())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException httpFailure) {
            int statusCode = httpFailure.getStatusCode().value();
            if (statusCode == 400
                    && httpFailure.getResponseBodyAsString().contains(INVALID_TOKEN_MARKER)) {
                // Token already invalid — same logical outcome as a fresh revocation.
                return;
            }
            log.warn("event=gmail_revoke_failed statusCode={}", statusCode);
        } catch (RestClientException transportFailure) {
            log.warn("event=gmail_revoke_failed reason=transport_error");
        }
    }
}
