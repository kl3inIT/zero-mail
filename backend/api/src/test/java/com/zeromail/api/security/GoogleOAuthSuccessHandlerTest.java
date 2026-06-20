package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.api.config.ApiProperties;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.account.usecases.OAuthProvisioningService;
import com.zeromail.core.admin.tenant.usecases.TenantActivityRecorder;
import com.zeromail.core.admin.tenant.usecases.TenantActivityRequestContext;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.rules.usecases.RuleTemplateMaterializationService;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Phase 01.5: updated to match the bundled-OAuth handler contract. {@link
 * GoogleOAuthSuccessHandler} now receives authorizedClients + users + provisioning.
 */
class GoogleOAuthSuccessHandlerTest {

    private static final ApiProperties PROPS =
            new ApiProperties(
                    new ApiProperties.WebProperties(URI.create("http://localhost:3000/")),
                    null /* null triggers CorsProperties defaults via compact constructor */,
                    null /* null triggers GmailProperties defaults via compact constructor */);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void redirects_to_frontend_onboarding_after_successful_bundled_grant() throws Exception {
        var provisioning = mock(OAuthProvisioningService.class);
        var authorizedClients = mock(OAuth2AuthorizedClientService.class);
        var userRepo = mock(UserRepository.class);
        var ruleTemplateMaterialization = mock(RuleTemplateMaterializationService.class);
        var tenantActivityRecorder = mock(TenantActivityRecorder.class);

        var handler =
                new GoogleOAuthSuccessHandler(
                        provisioning,
                        authorizedClients,
                        userRepo,
                        mock(GmailConnectionService.class),
                        ruleTemplateMaterialization,
                        tenantActivityRecorder,
                        Clock.systemUTC(),
                        PROPS);

        String subject = "google-subject-bundled-test";
        String email = "bundled-test@example.com";

        // Build OidcUser principal
        String profileDisplayName = "Bundled User";
        String profilePictureUrl = "https://lh3.googleusercontent.com/bundled-user";
        var claims =
                Map.<String, Object>of(
                        "sub",
                        subject,
                        "email",
                        email,
                        "name",
                        profileDisplayName,
                        "picture",
                        profilePictureUrl);
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
                        .scope(Set.of("openid", "profile", "email", OAuthScopes.GMAIL_MODIFY))
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .tokenUri("https://oauth2.googleapis.com/token")
                        .build();
        var accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "fake-at",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Set.of("openid", "profile", "email", OAuthScopes.GMAIL_MODIFY));
        var refreshToken = new OAuth2RefreshToken("fake-rt", Instant.now());
        when(authorizedClients.loadAuthorizedClient(eq("google"), anyString()))
                .thenReturn(
                        new OAuth2AuthorizedClient(
                                registration, token.getName(), accessToken, refreshToken));

        UUID provisionedTenantId = UUID.randomUUID();
        when(provisioning.provisionBundledOAuth(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn(
                        new OAuthProvisioningService.BundledProvisioningResult(
                                provisionedTenantId, UUID.randomUUID(), UUID.randomUUID(), true));

        var request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.17, 10.0.0.12");
        request.addHeader("CF-IPCity", "Ha Noi");
        request.addHeader("CF-IPCountry", "VN");
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, token);

        verify(provisioning)
                .provisionBundledOAuth(
                        eq(subject),
                        eq(email),
                        anyString(),
                        anyString(),
                        eq(profileDisplayName),
                        eq(profilePictureUrl));
        // First login seeds the Inbox-Zero-style default rules (enabled) for the new tenant. The
        // test OidcUser has no `locale` claim, so seeding falls back to Vietnamese (VN-first).
        verify(ruleTemplateMaterialization)
                .materializeDefaultRulesEnabled(provisionedTenantId, "vi");
        ArgumentCaptor<TenantActivityRequestContext> requestContextCaptor =
                ArgumentCaptor.forClass(TenantActivityRequestContext.class);
        ArgumentCaptor<Instant> occurredAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(tenantActivityRecorder)
                .recordLogin(
                        eq(provisionedTenantId),
                        requestContextCaptor.capture(),
                        occurredAtCaptor.capture());
        assertThat(
                        Arrays.stream(TenantActivityRequestContext.class.getRecordComponents())
                                .map(RecordComponent::getName))
                .doesNotContain("ipAddress", "locationLabel", "deviceFamily", "userAgent");
        assertThat(occurredAtCaptor.getValue()).isNotNull();
        assertThat(
                        request.getSession(false)
                                .getAttribute(TenantActivitySessionAttributes.TENANT_ID))
                .isEqualTo(provisionedTenantId.toString());
        assertThat(request.getSession(false).getAttribute(TenantActivitySessionAttributes.LOGIN_AT))
                .isNotNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/onboarding");
    }

    @Test
    void add_mailbox_uses_initiating_tenant_and_restores_initiating_session() throws Exception {
        var provisioning = mock(OAuthProvisioningService.class);
        var authorizedClients = mock(OAuth2AuthorizedClientService.class);
        var userRepository = mock(UserRepository.class);
        var gmailConnectionService = mock(GmailConnectionService.class);
        var ruleTemplateMaterialization = mock(RuleTemplateMaterializationService.class);
        var tenantActivityRecorder = mock(TenantActivityRecorder.class);
        var handler =
                new GoogleOAuthSuccessHandler(
                        provisioning,
                        authorizedClients,
                        userRepository,
                        gmailConnectionService,
                        ruleTemplateMaterialization,
                        tenantActivityRecorder,
                        Clock.systemUTC(),
                        PROPS);

        UUID initiatingTenantId = UUID.randomUUID();
        String initiatingSubject = "initiating-google-subject";
        String initiatingEmail = "primary@example.test";
        String addedMailboxSubject = "added-mailbox-google-subject";
        String addedMailboxEmail = "added@example.test";
        String addedMailboxName = "Added Mailbox";
        String addedMailboxPicture = "https://lh3.googleusercontent.com/added-mailbox";
        OAuth2AuthenticationToken initiatingAuthentication =
                authenticationToken(initiatingSubject, initiatingEmail);
        OAuth2AuthenticationToken addedMailboxAuthentication =
                authenticationToken(
                        addedMailboxSubject,
                        addedMailboxEmail,
                        addedMailboxName,
                        addedMailboxPicture);
        when(userRepository.findByGoogleSubject(initiatingSubject))
                .thenReturn(
                        Optional.of(
                                new UserEntity(
                                        UUID.randomUUID(),
                                        initiatingTenantId,
                                        initiatingSubject,
                                        initiatingEmail)));
        when(authorizedClients.loadAuthorizedClient("google", addedMailboxAuthentication.getName()))
                .thenReturn(
                        authorizedClient(
                                addedMailboxAuthentication,
                                "added-refresh-token",
                                Set.of("openid", "profile", "email", OAuthScopes.GMAIL_MODIFY)));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                OAuthIntentSnapshot.CALLBACK_INTENT_SESSION_ATTRIBUTE,
                new OAuthIntentSnapshot(
                        OAuthIntentSnapshot.INTENT_ADD_MAILBOX, null, initiatingTenantId));
        session.setAttribute(
                OAuthIntentSnapshot.INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE,
                new SecurityContextImpl(initiatingAuthentication));
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(addedMailboxAuthentication));
        var request = new MockHttpServletRequest();
        request.setSession(session);
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, addedMailboxAuthentication);

        verify(gmailConnectionService)
                .addConnection(
                        initiatingTenantId,
                        addedMailboxEmail,
                        OAuthScopes.GMAIL_MODIFY,
                        "added-refresh-token",
                        addedMailboxName,
                        addedMailboxPicture);
        assertThat(
                        session.getAttribute(
                                OAuthIntentSnapshot.INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE))
                .isNull();
        SecurityContext restoredSecurityContext =
                (SecurityContext)
                        Objects.requireNonNull(
                                session.getAttribute(
                                        HttpSessionSecurityContextRepository
                                                .SPRING_SECURITY_CONTEXT_KEY));
        assertThat(restoredSecurityContext.getAuthentication()).isSameAs(initiatingAuthentication);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(initiatingAuthentication);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/onboarding");
    }

    private static OAuth2AuthenticationToken authenticationToken(String subject, String email) {
        return authenticationToken(subject, email, null, null);
    }

    private static OAuth2AuthenticationToken authenticationToken(
            String subject, String email, String profileDisplayName, String profilePictureUrl) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        if (profileDisplayName != null) {
            claims.put("name", profileDisplayName);
        }
        if (profilePictureUrl != null) {
            claims.put("picture", profilePictureUrl);
        }
        var idToken =
                new OidcIdToken(
                        "test-token-" + subject,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        claims);
        var oidc =
                new DefaultOidcUser(
                        java.util.List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(oidc, oidc.getAuthorities(), "google");
    }

    private static OAuth2AuthorizedClient authorizedClient(
            OAuth2AuthenticationToken authenticationToken,
            String refreshTokenValue,
            Set<String> accessTokenScopes) {
        var accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "fake-at-" + authenticationToken.getName(),
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        accessTokenScopes);
        var refreshToken = new OAuth2RefreshToken(refreshTokenValue, Instant.now());
        return new OAuth2AuthorizedClient(
                clientRegistration(), authenticationToken.getName(), accessToken, refreshToken);
    }

    private static ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-cid")
                .clientSecret("test-cs")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google")
                .scope(Set.of("openid", "profile", "email", OAuthScopes.GMAIL_MODIFY))
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();
    }
}
