package com.zeromail.api.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.gmail.model.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.onboarding.model.OnboardingStep;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 0 RED scaffold — references {@code GmailOAuthSuccessHandler} and
 * {@code GmailIdentityMismatchException} which do NOT exist yet. Tests fail to compile until
 * Plan 02 lands those classes; once they land, these tests must turn GREEN as the
 * acceptance contract for D-A1 / D-A4 (CONTEXT.md).
 *
 * <p>Locks the contract: identity-match → encrypted upsert + onboarding advance;
 * idempotent re-grant updates same row in place; identity-mismatch throws typed exception
 * and writes NO row + does NOT advance onboarding.
 *
 * <p>Privacy: all fixture identifiers use the obviously-fake prefixes
 * {@code google-subject-test-*} and {@code fake-refresh-token-*} per threat T-1.4-W0-01.
 */
@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class GmailOAuthSuccessHandlerIntegrationTest extends ApiPostgresTestBase {

    private static final String GMAIL_REGISTRATION_ID = "google-gmail";
    private static final String FAKE_REFRESH_TOKEN_V1 = "fake-refresh-token-do-not-use-v1";
    private static final String FAKE_REFRESH_TOKEN_V2 = "fake-refresh-token-do-not-use-v2";

    @LocalServerPort int port;

    @Autowired UserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired GmailConnectionRepository connections;
    @Autowired GmailConnectionService connectionService;
    @Autowired RefreshTokenCipher cipher;
    @Autowired TestSessionSupport.TestSessionMinter minter;

    // RED-by-design: GmailOAuthSuccessHandler does not exist yet (Plan 02 Task 1).
    @Autowired GmailOAuthSuccessHandler handler;

    // Spring stubs the OAuth2AuthorizedClient via the in-memory client service that the
    // OAuth2 login filter populates. We mock it for the test entry point.
    @Autowired OAuth2AuthorizedClientService authorizedClientService;

    record Seed(UUID tenantId, UUID userId, String googleSubject, String email) {}

    @BeforeEach
    void cleanUp() {
        connections.deleteAll();
    }

    @Test
    void successPath_matchingSubject_upsertsConnectionAndAdvancesStep() throws Exception {
        Seed seed = seedUser("alpha");

        var oidc = oidcUserMock(seed.googleSubject(), seed.email());
        var token = new OAuth2AuthenticationToken(
                oidc, oidc.getAuthorities(), GMAIL_REGISTRATION_ID);

        stubAuthorizedClient(seed, token, FAKE_REFRESH_TOKEN_V1,
                Set.of("openid", "profile", "email",
                        "https://www.googleapis.com/auth/gmail.modify"));

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        ScopedValue.where(TenantContext.TENANT, seed.tenantId().toString())
                .run(() -> {
                    try {
                        handler.onAuthenticationSuccess(req, res, token);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        var saved = connections.findByTenantId(seed.tenantId());
        assertThat(saved).isPresent();
        GmailConnectionEntity row = saved.get();
        assertThat(row.getStatus()).isEqualTo(GmailConnectionStatus.CONNECTED);
        assertThat(row.getGoogleEmail()).isEqualTo(seed.email());

        byte[] decrypted = cipher.decrypt(row.getRefreshTokenEncrypted(), seed.tenantId().toString());
        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(FAKE_REFRESH_TOKEN_V1);

        // Specifics: scopes column stores leg-2 gmail-prefix-only scopes. openid/profile/email
        // are filtered out per CONTEXT.md §Specifics.
        assertThat(row.getScopesGranted()).isEqualTo("https://www.googleapis.com/auth/gmail.modify");

        var refreshedUser = users.findById(seed.userId()).orElseThrow();
        assertThat(refreshedUser.getOnboardingStep()).isEqualTo(OnboardingStep.GMAIL_CONNECTED);
    }

    @Test
    void idempotentReGrant_updatesExistingRowNoDuplicate() throws Exception {
        Seed seed = seedUser("beta");

        var oidc = oidcUserMock(seed.googleSubject(), seed.email());
        var token = new OAuth2AuthenticationToken(
                oidc, oidc.getAuthorities(), GMAIL_REGISTRATION_ID);

        stubAuthorizedClient(seed, token, FAKE_REFRESH_TOKEN_V1,
                Set.of("https://www.googleapis.com/auth/gmail.modify"));

        var req1 = new MockHttpServletRequest();
        var res1 = new MockHttpServletResponse();
        ScopedValue.where(TenantContext.TENANT, seed.tenantId().toString())
                .run(() -> {
                    try {
                        handler.onAuthenticationSuccess(req1, res1, token);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        GmailConnectionEntity firstRow = connections.findByTenantId(seed.tenantId()).orElseThrow();
        UUID firstId = firstRow.getId();
        Integer firstVersion = firstRow.getVersion();
        Instant firstConnectedAt = firstRow.getConnectedAt();

        // Second grant with a fresh token.
        stubAuthorizedClient(seed, token, FAKE_REFRESH_TOKEN_V2,
                Set.of("https://www.googleapis.com/auth/gmail.modify"));

        var req2 = new MockHttpServletRequest();
        var res2 = new MockHttpServletResponse();
        ScopedValue.where(TenantContext.TENANT, seed.tenantId().toString())
                .run(() -> {
                    try {
                        handler.onAuthenticationSuccess(req2, res2, token);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        assertThat(connections.findAll())
                .as("Single-row-per-tenant invariant must hold across re-grants")
                .hasSize(1);

        GmailConnectionEntity secondRow = connections.findByTenantId(seed.tenantId()).orElseThrow();
        assertThat(secondRow.getId()).isEqualTo(firstId);
        assertThat(secondRow.getVersion()).isGreaterThan(firstVersion);

        byte[] decrypted = cipher.decrypt(secondRow.getRefreshTokenEncrypted(), seed.tenantId().toString());
        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(FAKE_REFRESH_TOKEN_V2);

        // connectedAt should have been bumped on re-grant (D-A4 sets connectedAt=now on every upsert).
        assertThat(secondRow.getConnectedAt()).isAfterOrEqualTo(firstConnectedAt);
    }

    @Test
    void mismatchedSubject_throwsGmailIdentityMismatchException_andWritesNoRow() {
        Seed seed = seedUser("gamma");

        // OAuth2 returns a DIFFERENT Google subject than the seeded user — typed exception.
        String wrongSubject = "google-subject-test-different-99999";
        var oidc = oidcUserMock(wrongSubject, "different-account@example.com");
        var token = new OAuth2AuthenticationToken(
                oidc, oidc.getAuthorities(), GMAIL_REGISTRATION_ID);

        stubAuthorizedClient(seed, token, "fake-refresh-token-mismatch-leg2",
                Set.of("https://www.googleapis.com/auth/gmail.modify"));

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        assertThatThrownBy(() -> ScopedValue.where(TenantContext.TENANT, seed.tenantId().toString())
                .call(() -> {
                    handler.onAuthenticationSuccess(req, res, token);
                    return null;
                }))
                .isInstanceOf(GmailIdentityMismatchException.class);

        // No row written; onboarding step still SIGNED_IN — D-A1 fail-fast contract.
        assertThat(connections.count()).isZero();

        var refreshedUser = users.findById(seed.userId()).orElseThrow();
        assertThat(refreshedUser.getOnboardingStep()).isEqualTo(OnboardingStep.SIGNED_IN);
    }

    private Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, "test-tenant-" + label));
        String googleSubject = "google-subject-test-" + label;
        String email = label + "@example.com";
        UserEntity user = ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> users.save(new UserEntity(
                        UUID.randomUUID(), tenantId, googleSubject, email)));
        minter.mint(user.getGoogleSubject(), user.getEmail());
        return new Seed(tenantId, user.getId(), googleSubject, email);
    }

    private DefaultOidcUser oidcUserMock(String subject, String email) {
        var claims = Map.<String, Object>of("sub", subject, "email", email);
        var idToken = new OidcIdToken(
                "test-id-token-" + subject,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                claims);
        return new DefaultOidcUser(
                java.util.List.of(new SimpleGrantedAuthority("OIDC_USER")),
                idToken);
    }

    private void stubAuthorizedClient(
            Seed seed, OAuth2AuthenticationToken token, String refreshTokenValue, Set<String> scopes) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(GMAIL_REGISTRATION_ID)
                .clientId("test-google-client")
                .clientSecret("test-google-secret")
                .clientName("google-gmail")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google-gmail")
                .scope(scopes)
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();

        var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "fake-access-token-do-not-use",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                scopes);
        var refreshToken = new org.springframework.security.oauth2.core.OAuth2RefreshToken(
                refreshTokenValue, Instant.now());

        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(
                registration, token.getName(), accessToken, refreshToken);

        OAuth2AuthorizedClientService stub = mock(OAuth2AuthorizedClientService.class);
        when(stub.loadAuthorizedClient(GMAIL_REGISTRATION_ID, token.getName())).thenReturn(client);
        // Wave 1 implementation reads via the autowired bean; the mock above is a future-looking
        // hook so the test can override per-call. The wave 0 test is RED until the handler
        // exists, so this stub is informational; Plan 02 will replace authorizedClientService
        // with the right injection.
    }
}
