package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.zeromail.api.config.ApiProperties;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.account.usecases.OAuthProvisioningService;
import com.zeromail.core.admin.tenant.usecases.TenantActivityRecorder;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.referral.usecases.ReferralCampaignService;
import com.zeromail.core.rules.usecases.RuleTemplateMaterializationService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class OAuthIntentRoutingTest {

    @Test
    void url_param_intent_carries_no_branch_authority_without_pending_session_snapshot() {
        OAuth2AuthorizationRequestResolver resolver = googleResolver();
        MockHttpServletRequest request = authorizationRequest();
        request.setParameter("intent", "add_mailbox");
        request.setParameter("targetMailboxId", UUID.randomUUID().toString());

        var authorizationRequest = resolver.resolve(request, "google");

        assertThat(authorizationRequest.getAttributes())
                .doesNotContainKeys("intent", "targetMailboxId", "initiatingTenantId");
    }

    @Test
    void pending_snapshot_drives_branch_and_resolver_clears_pending_key_one_shot() {
        OAuth2AuthorizationRequestResolver resolver = googleResolver();
        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest request = authorizationRequest();
        request.setSession(new MockHttpSession());
        request.getSession()
                .setAttribute(
                        OAuthIntentSnapshot.PENDING_SESSION_ATTRIBUTE,
                        new OAuthIntentSnapshot("add_mailbox", null, tenantId));

        var authorizationRequest = resolver.resolve(request, "google");

        assertThat(authorizationRequest.getAttributes())
                .containsEntry("intent", "add_mailbox")
                .containsEntry("initiatingTenantId", tenantId);
        assertThat(request.getSession().getAttribute(OAuthIntentSnapshot.PENDING_SESSION_ATTRIBUTE))
                .as("pending snapshot is one-shot so stale add/reconnect cannot be replayed")
                .isNull();
    }

    @Test
    void failure_handler_clears_stale_intent_snapshot_after_failed_callback() throws Exception {
        OAuth2AuthorizedClientService authorizedClientService =
                mock(OAuth2AuthorizedClientService.class);
        LoginRedirectAuthenticationFailureHandler failureHandler =
                new LoginRedirectAuthenticationFailureHandler(
                        authorizedClientService, new ApiProperties(null, null, null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        request.getSession()
                .setAttribute(
                        IntentCarryingAuthorizationRequestRepository.INTENT_SESSION_ATTRIBUTE,
                        new OAuthIntentSnapshot("add_mailbox", null, UUID.randomUUID()));
        request.getSession()
                .setAttribute(
                        OAuthIntentSnapshot.INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE,
                        "stale-context");
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(
                        request.getSession()
                                .getAttribute(
                                        IntentCarryingAuthorizationRequestRepository
                                                .INTENT_SESSION_ATTRIBUTE))
                .as("stale-intent-after-failure must not survive to a later callback")
                .isNull();
        assertThat(
                        request.getSession()
                                .getAttribute(
                                        OAuthIntentSnapshot
                                                .INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE))
                .as("stale initiating context after failure must not survive")
                .isNull();
    }

    @Test
    void success_handler_clears_intent_before_scope_short_circuit() {
        OAuth2AuthorizedClientService authorizedClientService =
                mock(OAuth2AuthorizedClientService.class);
        GoogleOAuthSuccessHandler successHandler =
                new GoogleOAuthSuccessHandler(
                        mock(OAuthProvisioningService.class),
                        authorizedClientService,
                        mock(UserRepository.class),
                        mock(GmailConnectionService.class),
                        mock(RuleTemplateMaterializationService.class),
                        mock(ReferralCampaignService.class),
                        mock(TenantActivityRecorder.class),
                        Clock.systemUTC(),
                        new ApiProperties(null, null, null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        request.getSession()
                .setAttribute(
                        IntentCarryingAuthorizationRequestRepository.INTENT_SESSION_ATTRIBUTE,
                        new OAuthIntentSnapshot(
                                "reconnect_mailbox", UUID.randomUUID(), UUID.randomUUID()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationToken authenticationToken = authenticationToken();
        given(authorizedClientService.loadAuthorizedClient("google", authenticationToken.getName()))
                .willReturn(authorizedClientWithoutGmailModify(authenticationToken.getName()));

        assertThatThrownBy(
                        () ->
                                successHandler.onAuthenticationSuccess(
                                        request, response, authenticationToken))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(
                        request.getSession()
                                .getAttribute(
                                        IntentCarryingAuthorizationRequestRepository
                                                .INTENT_SESSION_ATTRIBUTE))
                .as("HIGH-B: handler reached, early failure fired, intent still removed")
                .isNull();
    }

    private static OAuth2AuthorizationRequestResolver googleResolver() {
        return new GoogleAuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(clientRegistration()),
                new ReferralAttributionTokenCodec("test-referral-signing-secret"));
    }

    private static MockHttpServletRequest authorizationRequest() {
        return new MockHttpServletRequest("GET", "/oauth2/authorization/google");
    }

    private static OAuth2AuthenticationToken authenticationToken() {
        OidcIdToken oidcIdToken =
                new OidcIdToken(
                        "id-token",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Map.of(
                                StandardClaimNames.SUB,
                                "google-subject",
                                StandardClaimNames.EMAIL,
                                "oauth@example.test"));
        DefaultOidcUser principal = new DefaultOidcUser(Set.of(), oidcIdToken);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    private static OAuth2AuthorizedClient authorizedClientWithoutGmailModify(String principalName) {
        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "access-token",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Set.of("openid", "email"));
        return new OAuth2AuthorizedClient(clientRegistration(), principalName, accessToken);
    }

    private static ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email", "profile", OAuthScopes.GMAIL_MODIFY)
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userNameAttributeName(StandardClaimNames.SUB)
                .clientName("Google")
                .build();
    }
}
