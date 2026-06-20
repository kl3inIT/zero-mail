package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.calendar.persistence.CalendarConnectionRepository;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.oauth.token.OAuthTokenStore;
import com.zeromail.core.oauth.token.OAuthTokenStore.RowDiscriminator;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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

/**
 * Phase 12 W1 explicit T-12-05 mitigation: run a Calendar OAuth round-trip for tenantA after
 * seeding a Gmail row, then assert the Gmail row's {@code refresh_token_encrypted} is
 * byte-identical pre/post the Calendar flow. Documents the Pitfall 2 invariant at the end-to-end
 * layer (the cipher-test pinned it at the persistence layer; this test pins it through the actual
 * {@code CalendarOAuthSuccessHandler} code path).
 */
@Import(CalendarOAuthTokenIsolationTest.MockAuthorizedClientServiceConfig.class)
class CalendarOAuthTokenIsolationTest extends ApiPostgresTestBase {

    private static final String CALENDAR_REGISTRATION_ID = "google-calendar";
    private static final String FAKE_CALENDAR_REFRESH_TOKEN =
            "fake-calendar-refresh-token-do-not-use-v1";
    private static final String FAKE_GMAIL_REFRESH_TOKEN = "fake-gmail-refresh-token-do-not-use-v1";

    @TestConfiguration
    static class MockAuthorizedClientServiceConfig {
        @Bean
        @Primary
        OAuth2AuthorizedClientService mockAuthorizedClientService() {
            return mock(OAuth2AuthorizedClientService.class);
        }
    }

    @Autowired CalendarOAuthSuccessHandler calendarOAuthSuccessHandler;
    @Autowired CalendarConnectionRepository calendarConnectionRepository;
    @Autowired GmailConnectionRepository gmailConnectionRepository;
    @Autowired OAuthTokenStore oAuthTokenStore;
    @Autowired OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM mailbox_calendar_preferences");
        jdbcTemplate.execute("DELETE FROM calendars");
        jdbcTemplate.execute("DELETE FROM calendar_connections");
        jdbcTemplate.execute("DELETE FROM gmail_connections");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void calendar_oauth_round_trip_leaves_gmail_connections_refresh_token_byte_identical()
            throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);

        // Step 1 — seed a Gmail row for tenantA with a known encrypted refresh token.
        byte[] gmailPlaintext = FAKE_GMAIL_REFRESH_TOKEN.getBytes(StandardCharsets.UTF_8);
        byte[] gmailEnvelope =
                oAuthTokenStore.encrypt(
                        gmailPlaintext, tenantId, RowDiscriminator.GMAIL_CONNECTION);
        UUID gmailConnectionId = UUID.randomUUID();
        TenantContext.runWith(
                tenantId,
                () -> {
                    GmailConnectionEntity gmailConnection =
                            new GmailConnectionEntity(
                                    gmailConnectionId,
                                    tenantId,
                                    "gmail-tenant-a-" + UUID.randomUUID() + "@example.test",
                                    GmailConnectionStatus.CONNECTED);
                    gmailConnection.setRefreshTokenEncrypted(gmailEnvelope);
                    gmailConnection.setConnectedAt(Instant.now());
                    gmailConnection.setScopesGranted("gmail.modify-placeholder");
                    gmailConnectionRepository.saveAndFlush(gmailConnection);
                });
        byte[] gmailBytesBefore = readGmailEnvelope(tenantId, gmailConnectionId);

        // Step 2 — run the Calendar OAuth flow for tenantA.
        String calendarEmail = "calendar-grant-" + UUID.randomUUID() + "@example.test";
        String principalName = "calendar-subject-" + UUID.randomUUID();
        OAuth2AuthenticationToken authenticationToken =
                buildAuthenticationToken(principalName, calendarEmail);
        stubAuthorizedClient(authenticationToken, FAKE_CALENDAR_REFRESH_TOKEN);

        TenantContext.runWith(
                tenantId,
                () -> {
                    try {
                        calendarOAuthSuccessHandler.onAuthenticationSuccess(
                                new MockHttpServletRequest(),
                                new MockHttpServletResponse(),
                                authenticationToken);
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    }
                });

        // Step 3 — assert Gmail row byte-identity.
        byte[] gmailBytesAfter = readGmailEnvelope(tenantId, gmailConnectionId);
        assertThat(gmailBytesAfter)
                .as(
                        "T-12-05 Pitfall 2: gmail_connections.refresh_token_encrypted MUST be"
                                + " byte-identical pre/post the Calendar OAuth flow")
                .isEqualTo(gmailBytesBefore);

        // Step 4 — assert Calendar row has its OWN encrypted token decrypting to the calendar
        // plaintext (independent envelope, not an accidental Gmail copy).
        TenantContext.runWith(
                tenantId,
                () -> {
                    List<com.zeromail.core.calendar.persistence.CalendarConnectionEntity>
                            calendarRows = calendarConnectionRepository.findAll();
                    assertThat(calendarRows).hasSize(1);
                    byte[] calendarBytes = calendarRows.getFirst().getRefreshTokenEncrypted();
                    assertThat(calendarBytes)
                            .as(
                                    "the Calendar row's envelope must be distinct from the Gmail"
                                            + " row's envelope")
                            .isNotEqualTo(gmailBytesBefore);
                    byte[] decryptedCalendar =
                            oAuthTokenStore.decrypt(
                                    calendarBytes, tenantId, RowDiscriminator.CALENDAR_CONNECTION);
                    assertThat(new String(decryptedCalendar, StandardCharsets.UTF_8))
                            .isEqualTo(FAKE_CALENDAR_REFRESH_TOKEN);
                });
    }

    private byte[] readGmailEnvelope(UUID tenantId, UUID gmailConnectionId) {
        java.util.concurrent.atomic.AtomicReference<byte[]> holder =
                new java.util.concurrent.atomic.AtomicReference<>();
        TenantContext.runWith(
                tenantId,
                () ->
                        holder.set(
                                gmailConnectionRepository
                                        .findByIdAndTenantId(gmailConnectionId, tenantId)
                                        .orElseThrow()
                                        .getRefreshTokenEncrypted()
                                        .clone()));
        return holder.get();
    }

    private OAuth2AuthenticationToken buildAuthenticationToken(
            String principalName, String googleEmail) {
        Map<String, Object> claims =
                Map.of(
                        "sub", principalName,
                        "email", googleEmail);
        OidcIdToken idToken =
                new OidcIdToken(
                        "test-id-token-" + principalName,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        claims);
        DefaultOidcUser principal =
                new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), CALENDAR_REGISTRATION_ID);
    }

    private void stubAuthorizedClient(
            OAuth2AuthenticationToken authenticationToken, String refreshTokenValue) {
        Set<String> calendarScopes =
                Set.of(
                        "https://www.googleapis.com/auth/calendar.freebusy",
                        "https://www.googleapis.com/auth/calendar.events",
                        "https://www.googleapis.com/auth/calendar.readonly");
        ClientRegistration registration =
                ClientRegistration.withRegistrationId(CALENDAR_REGISTRATION_ID)
                        .clientId("test-google-client")
                        .clientSecret("test-google-secret")
                        .clientName(CALENDAR_REGISTRATION_ID)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri(
                                "http://localhost/login/oauth2/code/" + CALENDAR_REGISTRATION_ID)
                        .scope(calendarScopes)
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .tokenUri("https://oauth2.googleapis.com/token")
                        .build();
        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "fake-access-token-do-not-use",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        calendarScopes);
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(refreshTokenValue, Instant.now());
        OAuth2AuthorizedClient authorizedClient =
                new OAuth2AuthorizedClient(
                        registration, authenticationToken.getName(), accessToken, refreshToken);
        when(oAuth2AuthorizedClientService.loadAuthorizedClient(
                        CALENDAR_REGISTRATION_ID, authenticationToken.getName()))
                .thenReturn(authorizedClient);
    }

    private void seedTenant(UUID tenantId) {
        jdbcTemplate.update(
                "INSERT INTO tenants (id, display_name, created_at) VALUES (?, ?, now())",
                tenantId,
                "test-tenant-" + tenantId);
    }
}
