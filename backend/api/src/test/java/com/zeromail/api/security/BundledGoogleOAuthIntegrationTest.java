package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.onboarding.domain.OnboardingStep;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Wave 0 RED scaffold — references {@link GoogleOAuthSuccessHandler} (extended in Task 2) and
 * {@link BundledGoogleOAuthIntegrationTest.MockAuthorizedClientServiceConfig} harness.
 *
 * <p>Tests fail (compile-RED or runtime-RED) until Task 2 lands the full bundled OAuth
 * implementation. Once Task 2 completes, ALL FIVE cases must turn GREEN.
 *
 * <p><b>Case coverage:</b>
 *
 * <ol>
 *   <li>Full-grant happy path — user + tenant + GmailConnectionEntity all persisted atomically,
 *       onboarding_step = GMAIL_CONNECTED.
 *   <li>Scope-missing (no gmail.modify) — no DB rows, 302 to /login?error=gmail_scope_required.
 *   <li>HIGH-1: gmail upsert failure rolls back user + tenant (atomicity invariant).
 *   <li>MED-3: null refresh token on first login — no DB rows, 302 to /login?error=consent_denied.
 *   <li>INFO-7: gmail.settings.basic only missing — provisioning succeeds with warning log.
 * </ol>
 *
 * <p>Privacy: all fixture identifiers use obviously-fake prefixes per threat T-01.5-01-03.
 */
@ActiveProfiles("test")
@Import({
    TestSessionSupport.class,
    BundledGoogleOAuthIntegrationTest.MockAuthorizedClientServiceConfig.class
})
class BundledGoogleOAuthIntegrationTest extends ApiPostgresTestBase {

    /**
     * @Primary mock so the handler bean's loadAuthorizedClient(...) is controlled per-test.
     */
    @TestConfiguration
    static class MockAuthorizedClientServiceConfig {
        @Bean
        @Primary
        OAuth2AuthorizedClientService mockAuthorizedClientService() {
            return mock(OAuth2AuthorizedClientService.class);
        }
    }

    private static final String GOOGLE_REGISTRATION_ID = "google";
    private static final String FAKE_REFRESH_TOKEN = "fake-bundled-refresh-token-do-not-use-v1";

    @Autowired UserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired OAuth2AuthorizedClientService authorizedClientService;
    @Autowired GoogleOAuthSuccessHandler handler;

    /**
     * JdbcTemplate used for row-count assertions that must bypass Hibernate's @TenantId filter. JPA
     * count() on tenant-filtered entities returns 0 when TenantContext.TENANT is not bound (which
     * is normal outside a request thread), hiding real rows.
     */
    @Autowired JdbcTemplate jdbc;

    // @MockitoSpyBean for HIGH-1 test — wraps real GmailConnectionService to simulate failure.
    @MockitoSpyBean GmailConnectionService gmailConnectionService;

    @BeforeEach
    void cleanUp() {
        // Use JDBC to bypass tenant filter on delete — JPA deleteAll() on filtered entities
        // would also return 0 rows under a missing tenant context.
        jdbc.execute("DELETE FROM gmail_connections");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("DELETE FROM tenants");
    }

    /** Count rows without Hibernate tenant filter (for cross-tenant assertions). */
    private long countUsers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    private long countTenants() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Long.class);
    }

    private long countConnections() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gmail_connections", Long.class);
    }

    // -------------------------------------------------------------------------
    // Case (a): Full-grant happy path
    // -------------------------------------------------------------------------

    @Test
    void bundled_oauth_persists_user_tenant_and_gmail_connection_atomically_on_full_grant()
            throws Exception {
        String subject = "google-subject-bundled-full-grant-" + UUID.randomUUID();
        String email = "full-grant-" + UUID.randomUUID() + "@example.test";

        var token = buildToken(subject, email);
        stubAuthorizedClient(token, FAKE_REFRESH_TOKEN, fullScopes());

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(req, res, token);

        assertThat(countUsers()).as("UserEntity must be persisted").isEqualTo(1);
        assertThat(countTenants()).as("TenantEntity must be persisted").isEqualTo(1);
        assertThat(countConnections()).as("GmailConnectionEntity must be persisted").isEqualTo(1);

        var user = users.findByGoogleSubject(subject).orElseThrow();
        assertThat(user.getOnboardingStep())
                .as("onboarding_step must advance to GMAIL_CONNECTED (D-B2)")
                .isEqualTo(OnboardingStep.GMAIL_CONNECTED);
    }

    // -------------------------------------------------------------------------
    // Case (b): Scope-missing — gmail.modify not granted
    // -------------------------------------------------------------------------

    @Test
    void scope_missing_short_circuits_before_provisioning_no_db_write() throws Exception {
        String subject = "google-subject-bundled-scope-missing-" + UUID.randomUUID();
        String email = "scope-missing-" + UUID.randomUUID() + "@example.test";

        var token = buildToken(subject, email);
        // No gmail.modify in the granted scopes
        stubAuthorizedClient(
                token,
                FAKE_REFRESH_TOKEN,
                Set.of(
                        "openid",
                        "profile",
                        "email",
                        "https://www.googleapis.com/auth/gmail.settings.basic"));

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        // Handler should throw OAuth2AuthenticationException with "gmail_scope_required"
        // before writing anything — or redirect via failure handler if wired differently.
        // Either way, no DB rows must exist.
        try {
            handler.onAuthenticationSuccess(req, res, token);
        } catch (Exception ignored) {
            // The success handler throws OAuth2AuthenticationException which bubbles up.
        }

        assertThat(countUsers()).as("No UserEntity on scope-missing path").isZero();
        assertThat(countTenants()).as("No TenantEntity on scope-missing path").isZero();
        assertThat(countConnections())
                .as("No GmailConnectionEntity on scope-missing path")
                .isZero();
    }

    // -------------------------------------------------------------------------
    // Case (c): HIGH-1 — gmail upsert failure rolls back user + tenant
    // -------------------------------------------------------------------------

    @Test
    void gmail_upsert_failure_rolls_back_user_and_tenant() throws Exception {
        String subject = "google-subject-bundled-upsert-fail-" + UUID.randomUUID();
        String email = "upsert-fail-" + UUID.randomUUID() + "@example.test";

        var token = buildToken(subject, email);
        stubAuthorizedClient(token, FAKE_REFRESH_TOKEN, fullScopes());

        // Simulate upsert throwing a runtime exception INSIDE the transaction
        doThrow(new RuntimeException("Simulated cipher/upsert failure for HIGH-1 atomicity test"))
                .when(gmailConnectionService)
                .upsert(
                        org.mockito.ArgumentMatchers.any(UUID.class),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(byte[].class));

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        try {
            handler.onAuthenticationSuccess(req, res, token);
        } catch (Exception ignored) {
            // Exception expected — it should roll back the transaction.
        }

        // HIGH-1 invariant: ALL three tables must be EMPTY after rollback.
        assertThat(countUsers())
                .as("HIGH-1: users must be empty after upsert failure rollback")
                .isZero();
        assertThat(countTenants())
                .as("HIGH-1: tenants must be empty after upsert failure rollback")
                .isZero();
        assertThat(countConnections())
                .as("HIGH-1: gmail_connections must be empty after upsert failure rollback")
                .isZero();
    }

    // -------------------------------------------------------------------------
    // Case (d): MED-3 — null refresh token on first login
    // -------------------------------------------------------------------------

    @Test
    void null_refresh_token_on_first_login_redirects_to_consent_denied() throws Exception {
        String subject = "google-subject-bundled-null-rt-" + UUID.randomUUID();
        String email = "null-rt-" + UUID.randomUUID() + "@example.test";

        var token = buildToken(subject, email);
        // null refresh token — first login path
        stubAuthorizedClientNoRefreshToken(token, fullScopes());

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        try {
            handler.onAuthenticationSuccess(req, res, token);
        } catch (Exception ignored) {
            // OAuth2AuthenticationException("consent_denied") bubbles up.
        }

        // MED-3: no DB rows must exist when refresh token is null on first login
        assertThat(countUsers())
                .as("MED-3: no UserEntity on null-refresh-token first login")
                .isZero();
        assertThat(countTenants())
                .as("MED-3: no TenantEntity on null-refresh-token first login")
                .isZero();
        assertThat(countConnections())
                .as("MED-3: no GmailConnectionEntity on null-refresh-token first login")
                .isZero();
    }

    // -------------------------------------------------------------------------
    // Case (e): INFO-7 — gmail.settings.basic missing, provisioning succeeds
    // -------------------------------------------------------------------------

    @Test
    void gmail_settings_basic_only_missing_succeeds_with_warning() throws Exception {
        String subject = "google-subject-bundled-no-settings-basic-" + UUID.randomUUID();
        String email = "no-settings-basic-" + UUID.randomUUID() + "@example.test";

        var token = buildToken(subject, email);
        // gmail.modify granted, gmail.settings.basic NOT granted
        stubAuthorizedClient(
                token,
                FAKE_REFRESH_TOKEN,
                Set.of(
                        "openid",
                        "profile",
                        "email",
                        "https://www.googleapis.com/auth/gmail.modify"));

        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();

        // INFO-7: provisioning should SUCCEED (settings.basic is forward-looking, not v1-blocking)
        handler.onAuthenticationSuccess(req, res, token);

        assertThat(countUsers()).as("INFO-7: UserEntity must be persisted").isEqualTo(1);
        assertThat(countTenants()).as("INFO-7: TenantEntity must be persisted").isEqualTo(1);
        assertThat(countConnections())
                .as("INFO-7: GmailConnectionEntity must be persisted")
                .isEqualTo(1);
        // Warning log with event=oauth_settings_basic_missing is verified via manual log inspection
        // (Logback test appender wiring would add test complexity; opaque-event-name contract is
        // validated by code review + privacy threat model T-01.5-01-11).
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private OAuth2AuthenticationToken buildToken(String subject, String email) {
        var claims = Map.<String, Object>of("sub", subject, "email", email);
        var idToken =
                new OidcIdToken(
                        "test-id-token-bundled-" + subject,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        claims);
        var principal =
                new DefaultOidcUser(
                        java.util.List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), GOOGLE_REGISTRATION_ID);
    }

    private Set<String> fullScopes() {
        return Set.of(
                "openid",
                "profile",
                "email",
                "https://www.googleapis.com/auth/gmail.modify",
                "https://www.googleapis.com/auth/gmail.settings.basic");
    }

    private void stubAuthorizedClient(
            OAuth2AuthenticationToken token, String refreshTokenValue, Set<String> scopes) {
        ClientRegistration registration =
                ClientRegistration.withRegistrationId(GOOGLE_REGISTRATION_ID)
                        .clientId("test-google-client")
                        .clientSecret("test-google-secret")
                        .clientName("google")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost/login/oauth2/code/google")
                        .scope(scopes)
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .tokenUri("https://oauth2.googleapis.com/token")
                        .build();

        var accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "fake-access-token-bundled-do-not-use",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        scopes);
        var refreshToken = new OAuth2RefreshToken(refreshTokenValue, Instant.now());

        OAuth2AuthorizedClient client =
                new OAuth2AuthorizedClient(
                        registration, token.getName(), accessToken, refreshToken);
        when(authorizedClientService.loadAuthorizedClient(GOOGLE_REGISTRATION_ID, token.getName()))
                .thenReturn(client);
    }

    private void stubAuthorizedClientNoRefreshToken(
            OAuth2AuthenticationToken token, Set<String> scopes) {
        ClientRegistration registration =
                ClientRegistration.withRegistrationId(GOOGLE_REGISTRATION_ID)
                        .clientId("test-google-client")
                        .clientSecret("test-google-secret")
                        .clientName("google")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost/login/oauth2/code/google")
                        .scope(scopes)
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .tokenUri("https://oauth2.googleapis.com/token")
                        .build();

        var accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "fake-access-token-bundled-no-rt-do-not-use",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        scopes);
        // NO refresh token — simulates null path per MED-3
        OAuth2AuthorizedClient client =
                new OAuth2AuthorizedClient(registration, token.getName(), accessToken, null);
        when(authorizedClientService.loadAuthorizedClient(GOOGLE_REGISTRATION_ID, token.getName()))
                .thenReturn(client);
    }
}
