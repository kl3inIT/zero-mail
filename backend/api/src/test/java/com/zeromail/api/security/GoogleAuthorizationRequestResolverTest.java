package com.zeromail.api.security;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 0 RED scaffold — exercises {@link GoogleAuthorizationRequestResolver} behaviour:
 * {@code prompt=consent} added only when {@code ?reconnect=true}, omitted on first-time login;
 * {@code access_type=offline} and {@code include_granted_scopes=true} always present.
 * Login_hint injection removed (Phase 01.5 D-A3 — same-account guarantee no longer needed).
 *
 * <p>Tests fail to compile until Task 2 creates {@link GoogleAuthorizationRequestResolver}.
 * Once Task 2 lands, all tests must turn GREEN.
 */
class GoogleAuthorizationRequestResolverTest {

    private static final String GOOGLE_REGISTRATION_ID = "google";

    @Test
    void prompt_consent_added_when_reconnect_query_param_true() {
        ClientRegistrationRepository repo = mockGoogleRegistry();
        var resolver = new GoogleAuthorizationRequestResolver(repo);

        var req = new MockHttpServletRequest();
        req.setRequestURI("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        req.setServletPath("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        req.setMethod("GET");
        req.setParameter("reconnect", "true");

        OAuth2AuthorizationRequest result = resolver.resolve(req, GOOGLE_REGISTRATION_ID);

        assertThat(result).isNotNull();
        Map<String, Object> extra = result.getAdditionalParameters();
        assertThat(extra)
                .as("prompt=consent must be added when reconnect=true")
                .containsEntry("prompt", "consent");
    }

    @Test
    void prompt_consent_omitted_on_first_time_login() {
        ClientRegistrationRepository repo = mockGoogleRegistry();
        var resolver = new GoogleAuthorizationRequestResolver(repo);

        var req = new MockHttpServletRequest();
        req.setRequestURI("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        req.setServletPath("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        req.setMethod("GET");
        // No reconnect parameter set

        OAuth2AuthorizationRequest result = resolver.resolve(req, GOOGLE_REGISTRATION_ID);

        assertThat(result).isNotNull();
        Map<String, Object> extra = result.getAdditionalParameters();
        assertThat(extra)
                .as("prompt=consent must NOT be added on first-time login (smooth UX)")
                .doesNotContainKey("prompt");
    }

    @Test
    void access_type_offline_always_present() {
        ClientRegistrationRepository repo = mockGoogleRegistry();
        var resolver = new GoogleAuthorizationRequestResolver(repo);

        var req = new MockHttpServletRequest();
        req.setRequestURI("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        req.setServletPath("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        req.setMethod("GET");

        OAuth2AuthorizationRequest result = resolver.resolve(req, GOOGLE_REGISTRATION_ID);

        assertThat(result).isNotNull();
        assertThat(result.getAdditionalParameters())
                .as("access_type=offline must always be present (refresh token issuance)")
                .containsEntry("access_type", "offline");
    }

    @Test
    void include_granted_scopes_always_true() {
        ClientRegistrationRepository repo = mockGoogleRegistry();
        var resolver = new GoogleAuthorizationRequestResolver(repo);

        var req = new MockHttpServletRequest();
        req.setRequestURI("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        req.setServletPath("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        req.setMethod("GET");

        OAuth2AuthorizationRequest result = resolver.resolve(req, GOOGLE_REGISTRATION_ID);

        assertThat(result).isNotNull();
        assertThat(result.getAdditionalParameters())
                .as("include_granted_scopes=true must always be present")
                .containsEntry("include_granted_scopes", "true");
    }

    private ClientRegistrationRepository mockGoogleRegistry() {
        ClientRegistration registration = ClientRegistration.withRegistrationId(GOOGLE_REGISTRATION_ID)
                .clientId("test-google-client")
                .clientSecret("test-google-secret")
                .clientName("google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google")
                .scope(List.of(
                        "openid",
                        "profile",
                        "email",
                        "https://www.googleapis.com/auth/gmail.modify",
                        "https://www.googleapis.com/auth/gmail.settings.basic"))
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();
        ClientRegistrationRepository repo = mock(ClientRegistrationRepository.class);
        when(repo.findByRegistrationId(eq(GOOGLE_REGISTRATION_ID))).thenReturn(registration);
        return repo;
    }
}
