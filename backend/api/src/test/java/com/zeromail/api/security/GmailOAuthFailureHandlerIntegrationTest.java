package com.zeromail.api.security;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.zeromail.api.security.testfixtures.MockGoogleRevocationServer;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 0 RED scaffold — references {@code GmailOAuthFailureHandler} and
 * {@code GmailIdentityMismatchException} which do NOT exist yet (Plan 02 lands them).
 *
 * <p>Locks the contract for D-A3 / D-D4 (CONTEXT.md):
 * <ul>
 *   <li>Identity mismatch → POST Google revoke endpoint (with leg-2 token), then redirect
 *       {@code /onboarding?error=gmail_identity_mismatch}.</li>
 *   <li>Consent denied (Google {@code error=access_denied}) → redirect
 *       {@code /onboarding?error=gmail_consent_denied}, NO revoke (no token to drop).</li>
 *   <li>Revoke endpoint 5xx → still redirect (best-effort cleanup per §Specifics).</li>
 * </ul>
 *
 * <p>Privacy: every fake token is obviously synthetic ({@code fake-token-*}) and never logged.
 */
@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class GmailOAuthFailureHandlerIntegrationTest extends ApiPostgresTestBase {

    private static final MockGoogleRevocationServer MOCK_SERVER = new MockGoogleRevocationServer();

    @Autowired GmailConnectionRepository connections;

    // RED-by-design: GmailOAuthFailureHandler does not exist yet (Plan 02 Task 1).
    @Autowired GmailOAuthFailureHandler handler;

    @BeforeAll
    static void startMockServer() throws IOException {
        MOCK_SERVER.start();
    }

    @AfterAll
    static void stopMockServer() {
        MOCK_SERVER.stop();
    }

    @DynamicPropertySource
    static void overrideRevokeEndpoint(DynamicPropertyRegistry r) {
        r.add("google.revoke.base-url", () -> "http://127.0.0.1:" + MOCK_SERVER.port());
    }

    @Test
    void identityMismatch_redirectsToOnboardingWithErrorCode_andRevokesToken() throws Exception {
        MOCK_SERVER.clearReceivedTokens();

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();
        var ex = new GmailIdentityMismatchException("fake-token-to-revoke-mismatch");

        handler.onAuthenticationFailure(req, res, ex);

        assertThat(res.getRedirectedUrl())
                .isNotNull()
                .endsWith("/onboarding?error=gmail_identity_mismatch");
        assertThat(MOCK_SERVER.getReceivedTokens())
                .as("Failure handler must POST the leg-2 token to Google's revoke endpoint")
                .contains("fake-token-to-revoke-mismatch");
        assertThat(connections.count())
                .as("No row may be persisted on identity mismatch — D-A1 fail-fast")
                .isZero();
    }

    @Test
    void consentDenied_redirectsToOnboardingWithErrorCode_noRevoke() throws Exception {
        MOCK_SERVER.clearReceivedTokens();

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();
        var ex = new OAuth2AuthenticationException(new OAuth2Error("access_denied"));

        handler.onAuthenticationFailure(req, res, ex);

        assertThat(res.getRedirectedUrl())
                .isNotNull()
                .endsWith("/onboarding?error=gmail_consent_denied");
        assertThat(MOCK_SERVER.getReceivedTokens())
                .as("No revoke when user denied consent — there is no token to revoke")
                .isEmpty();
    }

    @Test
    void revokeEndpoint5xx_doesNotBlockRedirect() throws Exception {
        MOCK_SERVER.clearReceivedTokens();
        MOCK_SERVER.setNextResponseStatus(500);

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();
        var ex = new GmailIdentityMismatchException("fake-token-to-revoke-5xx");

        handler.onAuthenticationFailure(req, res, ex);

        assertThat(res.getRedirectedUrl())
                .as("Best-effort revoke: 5xx must not block the user-facing redirect")
                .isNotNull()
                .endsWith("/onboarding?error=gmail_identity_mismatch");
    }
}
