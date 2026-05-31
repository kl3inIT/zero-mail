package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.zeromail.api.config.ApiProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Wave 0 RED scaffold — tests fail to compile until Task 2 creates {@link
 * LoginRedirectAuthenticationFailureHandler}. Once Task 2 lands, all tests must turn GREEN.
 */
class LoginRedirectAuthenticationFailureHandlerTest {

    private static final ApiProperties PROPS =
            new ApiProperties(
                    new ApiProperties.WebProperties(URI.create("http://localhost:3000")),
                    null /* null triggers CorsProperties defaults via compact constructor */,
                    null /* null triggers GmailProperties defaults via compact constructor */);

    @Test
    void access_denied_redirects_to_login_consent_denied() throws Exception {
        OAuth2AuthorizedClientService authorizedClients = mock(OAuth2AuthorizedClientService.class);
        var handler = new LoginRedirectAuthenticationFailureHandler(authorizedClients, PROPS);

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        var ex = new OAuth2AuthenticationException(new OAuth2Error("access_denied"));
        handler.onAuthenticationFailure(req, res, ex);

        assertThat(res.getRedirectedUrl())
                .as("access_denied must redirect to /login?error=consent_denied")
                .endsWith("/login?error=consent_denied");
        // removeAuthorizedClient must NOT be called on access_denied (no partial grant)
        verify(authorizedClients, never()).removeAuthorizedClient(anyString(), anyString());
    }

    @Test
    void gmail_scope_required_removes_authorized_client_then_redirects() throws Exception {
        OAuth2AuthorizedClientService authorizedClients = mock(OAuth2AuthorizedClientService.class);
        var handler = new LoginRedirectAuthenticationFailureHandler(authorizedClients, PROPS);

        var req = new MockHttpServletRequest();
        req.setUserPrincipal(() -> "test-principal");
        var res = new MockHttpServletResponse();

        var ex = new OAuth2AuthenticationException(new OAuth2Error("gmail_scope_required"));
        handler.onAuthenticationFailure(req, res, ex);

        // Best-effort removeAuthorizedClient must be called to clean up orphaned grant
        verify(authorizedClients).removeAuthorizedClient(eq("google"), anyString());
        assertThat(res.getRedirectedUrl())
                .as("gmail_scope_required must redirect to /login?error=gmail_scope_required")
                .endsWith("/login?error=gmail_scope_required");
    }

    @Test
    void unknown_oauth2_error_falls_through_to_super() throws Exception {
        OAuth2AuthorizedClientService authorizedClients = mock(OAuth2AuthorizedClientService.class);
        var handler = new LoginRedirectAuthenticationFailureHandler(authorizedClients, PROPS);
        // Configure a default failure URL for super.onAuthenticationFailure
        handler.setDefaultFailureUrl("/login?error");

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        var ex = new OAuth2AuthenticationException(new OAuth2Error("unknown_error_code"));
        handler.onAuthenticationFailure(req, res, ex);

        // Should have fallen through to default handler (redirects to /login?error)
        assertThat(res.getRedirectedUrl())
                .as("Unknown OAuth2 errors should fall through to super.onAuthenticationFailure")
                .isNotNull();
        assertThat(res.getRedirectedUrl())
                .doesNotEndWith("consent_denied")
                .doesNotEndWith("gmail_scope_required");
    }

    @Test
    void non_oauth2_exception_falls_through_to_super() throws Exception {
        OAuth2AuthorizedClientService authorizedClients = mock(OAuth2AuthorizedClientService.class);
        var handler = new LoginRedirectAuthenticationFailureHandler(authorizedClients, PROPS);
        handler.setDefaultFailureUrl("/login?error");

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        AuthenticationException ex = new AuthenticationException("generic auth failure") {};
        handler.onAuthenticationFailure(req, res, ex);

        // Should have fallen through to default handler
        assertThat(res.getRedirectedUrl())
                .as("Non-OAuth2 exceptions should fall through to super.onAuthenticationFailure")
                .isNotNull();
        verify(authorizedClients, never()).removeAuthorizedClient(anyString(), anyString());
    }
}
