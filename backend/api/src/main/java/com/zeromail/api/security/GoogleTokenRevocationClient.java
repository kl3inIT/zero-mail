package com.zeromail.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Best-effort wrapper around Google's token revocation endpoint
 * ({@code https://oauth2.googleapis.com/revoke}) used by {@code GmailOAuthFailureHandler}
 * on the identity-mismatch path to drop the just-granted but unwanted leg-2 token
 * (D-A3, threat T-1.4-02-stale-grant).
 *
 * <p>Per CONTEXT §Specifics ("Revoke endpoint is best-effort") this client never
 * blocks the user-facing redirect on a non-2xx, slow network, or transport failure.
 * Pitfall 8 — never retry on the same token; each mismatch attempt has a fresh one.
 *
 * <p><b>Privacy contract (D-E1, threat T-1.4-02-token-leak / T-1.4-02-revoke-token-leak):</b>
 * the token bytes are sent over HTTPS to Google as a URL-encoded query parameter
 * (Spring's URI builder URL-encodes safely). Log statements emit only opaque event
 * names — never the token contents.
 *
 * <p>Base URL is injected via {@code google.revoke.base-url} so integration tests
 * can override it via {@code @DynamicPropertySource} pointing at
 * {@code MockGoogleRevocationServer}.
 */
@Component
public class GoogleTokenRevocationClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenRevocationClient.class);

    private final RestClient client;

    public GoogleTokenRevocationClient(
            @Value("${google.revoke.base-url:https://oauth2.googleapis.com}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Best-effort POST to {@code /revoke?token=...}. Swallows transport / non-2xx
     * failures; never logs the token. No-op when the token is null or empty.
     */
    public void revokeQuietly(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            client.post()
                    .uri(uriBuilder -> uriBuilder.path("/revoke")
                            .queryParam("token", token)
                            .build())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .retrieve()
                    .toBodilessEntity();
            log.info("event=google_revoke_success");
        } catch (RestClientException e) {
            // Best-effort. NEVER log token contents (Threat T-1.4-02-revoke-token-leak / D-E1).
            log.warn("event=google_revoke_non_2xx");
        }
    }
}
