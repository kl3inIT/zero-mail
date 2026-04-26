package com.zeromail.api.security;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 0 RED-ish scaffold — exercises the {@link GmailScopeRequestResolver#resolve} path
 * with the {@code login_hint} extension that Plan 02 introduces. The Phase 1 resolver does
 * NOT add {@code login_hint} today, so the first test fails (no key in additional params).
 * Once Plan 02 lands the {@code login_hint} read from {@link SecurityContextHolder}, the
 * test must turn GREEN.
 *
 * <p>Locks D-A2 (CONTEXT.md): {@code login_hint} appears when an authenticated principal
 * exposes an email; gracefully omitted when SecurityContextHolder is empty
 * (no exception thrown). Existing parameters ({@code prompt=consent},
 * {@code access_type=offline}, {@code include_granted_scopes=true}) must remain.
 */
class GmailScopeRequestResolverTest {

    private static final String GMAIL_REGISTRATION_ID = "google-gmail";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginHintInjected_whenAuthenticatedSession() {
        ClientRegistrationRepository repo = mockGmailRegistry();
        var resolver = new GmailScopeRequestResolver(repo);

        // Authenticated principal: email "alice@example.com"
        SecurityContextHolder.getContext().setAuthentication(authenticatedToken());

        var req = new MockHttpServletRequest();
        req.setRequestURI("/oauth2/authorization/" + GMAIL_REGISTRATION_ID);
        req.setServletPath("/oauth2/authorization/" + GMAIL_REGISTRATION_ID);
        req.setMethod("GET");

        OAuth2AuthorizationRequest result = resolver.resolve(req, GMAIL_REGISTRATION_ID);

        assertThat(result).isNotNull();
        Map<String, Object> extra = result.getAdditionalParameters();
        assertThat(extra)
                .as("login_hint must default to the authenticated user's email — D-A2")
                .containsEntry("login_hint", "alice@example.com");
        // Existing params survive (no regression on the Phase 1 resolver behavior).
        assertThat(extra).containsEntry("prompt", "consent");
        assertThat(extra).containsEntry("access_type", "offline");
        assertThat(extra).containsEntry("include_granted_scopes", "true");
    }

    @Test
    void loginHintOmittedGracefully_whenAnonymousSession() {
        ClientRegistrationRepository repo = mockGmailRegistry();
        var resolver = new GmailScopeRequestResolver(repo);

        SecurityContextHolder.clearContext();

        var req = new MockHttpServletRequest();
        req.setRequestURI("/oauth2/authorization/" + GMAIL_REGISTRATION_ID);
        req.setServletPath("/oauth2/authorization/" + GMAIL_REGISTRATION_ID);
        req.setMethod("GET");

        OAuth2AuthorizationRequest result = resolver.resolve(req, GMAIL_REGISTRATION_ID);

        assertThat(result).isNotNull();
        Map<String, Object> extra = result.getAdditionalParameters();
        // Omission contract — key absent, NOT present-with-null.
        assertThat(extra)
                .as("Anonymous sessions degrade gracefully — the subject check is the authoritative guard")
                .doesNotContainKey("login_hint");
        // Existing params still present.
        assertThat(extra).containsEntry("prompt", "consent");
    }

    private ClientRegistrationRepository mockGmailRegistry() {
        ClientRegistration registration = ClientRegistration.withRegistrationId(GMAIL_REGISTRATION_ID)
                .clientId("test-google-client")
                .clientSecret("test-google-secret")
                .clientName("google-gmail")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google-gmail")
                .scope(List.of("https://www.googleapis.com/auth/gmail.modify"))
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();
        ClientRegistrationRepository repo = mock(ClientRegistrationRepository.class);
        when(repo.findByRegistrationId(eq(GMAIL_REGISTRATION_ID))).thenReturn(registration);
        return repo;
    }

    private OAuth2AuthenticationToken authenticatedToken() {
        var claims = Map.<String, Object>of(
                "sub", "google-subject-test-resolver",
                "email", "alice@example.com");
        var idToken = new OidcIdToken(
                "test-id-token-resolver",
                java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(3600),
                claims);
        var principal = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("OIDC_USER")),
                idToken);
        return new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "google");
    }
}
