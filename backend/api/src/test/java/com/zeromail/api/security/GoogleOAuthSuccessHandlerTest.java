package com.zeromail.api.security;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.zeromail.api.config.ZeroMailApiProperties;
import com.zeromail.core.account.service.OAuthProvisioningService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleOAuthSuccessHandlerTest {

    @Test
    void redirects_to_frontend_onboarding_after_successful_google_login() throws Exception {
        var provisioning = mock(OAuthProvisioningService.class);
        var handler = new GoogleOAuthSuccessHandler(
                provisioning,
                new ZeroMailApiProperties(
                        new ZeroMailApiProperties.WebProperties(URI.create("http://localhost:3000/")),
                        null));
        var oidc = mock(OidcUser.class);
        when(oidc.getSubject()).thenReturn("google-subject");
        when(oidc.getEmail()).thenReturn("user@example.com");

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var auth = new TestingAuthenticationToken(oidc, "");

        handler.onAuthenticationSuccess(request, response, auth);

        verify(provisioning).findOrCreateGoogleUser("google-subject", "user@example.com");
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/onboarding");
    }
}
