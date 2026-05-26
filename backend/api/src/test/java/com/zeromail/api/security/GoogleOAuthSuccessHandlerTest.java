package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.api.config.ZeroMailApiProperties;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.account.usecases.OAuthProvisioningService;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * Phase 01.5: updated to match the bundled-OAuth handler contract. {@link
 * GoogleOAuthSuccessHandler} now receives authorizedClients + users + provisioning.
 */
class GoogleOAuthSuccessHandlerTest {

    private static final ZeroMailApiProperties PROPS =
            new ZeroMailApiProperties(
                    new ZeroMailApiProperties.WebProperties(URI.create("http://localhost:3000/")),
                    null /* null triggers CorsProperties defaults via compact constructor */,
                    null /* null triggers GmailProperties defaults via compact constructor */);

    @Test
    void redirects_to_frontend_onboarding_after_successful_bundled_grant() throws Exception {
        var provisioning = mock(OAuthProvisioningService.class);
        var authorizedClients = mock(OAuth2AuthorizedClientService.class);
        var userRepo = mock(UserRepository.class);

        var handler =
                new GoogleOAuthSuccessHandler(provisioning, authorizedClients, userRepo, PROPS);

        String subject = "google-subject-bundled-test";
        String email = "bundled-test@example.com";

        // Build OidcUser principal
        var claims = Map.<String, Object>of("sub", subject, "email", email);
        var idToken =
                new OidcIdToken(
                        "test-token", Instant.now(), Instant.now().plusSeconds(3600), claims);
        var oidc =
                new DefaultOidcUser(
                        java.util.List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        var token = new OAuth2AuthenticationToken(oidc, oidc.getAuthorities(), "google");

        // Stub authorized client with full scopes + refresh token
        var registration =
                ClientRegistration.withRegistrationId("google")
                        .clientId("test-cid")
                        .clientSecret("test-cs")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost/login/oauth2/code/google")
                        .scope(
                                Set.of(
                                        "openid",
                                        "profile",
                                        "email",
                                        OAuthScopes.GMAIL_MODIFY))
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .tokenUri("https://oauth2.googleapis.com/token")
                        .build();
        var accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "fake-at",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Set.of(
                                "openid",
                                "profile",
                                "email",
                                OAuthScopes.GMAIL_MODIFY));
        var refreshToken = new OAuth2RefreshToken("fake-rt", Instant.now());
        when(authorizedClients.loadAuthorizedClient(eq("google"), anyString()))
                .thenReturn(
                        new OAuth2AuthorizedClient(
                                registration, token.getName(), accessToken, refreshToken));

        when(provisioning.provisionBundledOAuth(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(
                        new OAuthProvisioningService.BundledProvisioningResult(
                                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), true));

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, token);

        verify(provisioning)
                .provisionBundledOAuth(eq(subject), eq(email), anyString(), anyString());
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/onboarding");
    }
}
