package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.calendar.persistence.CalendarConnectionEntity;
import com.zeromail.core.calendar.persistence.CalendarConnectionRepository;
import com.zeromail.core.oauth.token.OAuthTokenStore;
import com.zeromail.core.oauth.token.OAuthTokenStore.RowDiscriminator;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
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
 * Integration test for {@link CalendarOAuthSuccessHandler}. Boots a real {@code @SpringBootTest}
 * context + Postgres Testcontainer so the JPA write, the Liquibase schema, the OAuthTokenStore
 * cipher, and the partial-unique-index race path all run end-to-end.
 *
 * <p>Mocks only the upstream {@link OAuth2AuthorizedClientService} (we never want a real Google
 * call in {@code ./gradlew test} per TESTING.md).
 *
 * <p>Pins:
 *
 * <ul>
 *   <li>A successful Calendar grant persists exactly one {@code calendar_connections} row with
 *       {@code status=CONNECTED} and the refresh token decrypts cleanly via {@code
 *       RowDiscriminator.CALENDAR_CONNECTION}.
 *   <li>The same flow does NOT write to {@code gmail_connections} (delegated to {@code
 *       CalendarOAuthTokenIsolationTest} which asserts byte-identity end-to-end).
 * </ul>
 */
@Import(CalendarOAuthSuccessHandlerTest.MockAuthorizedClientServiceConfig.class)
class CalendarOAuthSuccessHandlerTest extends ApiPostgresTestBase {

    private static final String CALENDAR_REGISTRATION_ID = "google-calendar";
    private static final String FAKE_CALENDAR_REFRESH_TOKEN =
            "fake-calendar-refresh-token-do-not-use-v1";

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
    @Autowired OAuthTokenStore oAuthTokenStore;
    @Autowired OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM mailbox_calendar_preferences");
        jdbcTemplate.execute("DELETE FROM calendars");
        jdbcTemplate.execute("DELETE FROM calendar_connections");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void calendar_oauth_round_trip_persists_one_connected_row_with_decryptable_refresh_token()
            throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        String googleEmail = "calendar-grant-" + UUID.randomUUID() + "@example.test";
        String principalName = "calendar-subject-" + UUID.randomUUID();

        OAuth2AuthenticationToken authenticationToken =
                buildAuthenticationToken(principalName, googleEmail);
        stubAuthorizedClient(authenticationToken, FAKE_CALENDAR_REFRESH_TOKEN);

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        TenantContext.runWith(
                tenantId,
                () -> {
                    try {
                        calendarOAuthSuccessHandler.onAuthenticationSuccess(
                                request, response, authenticationToken);
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    }
                });

        List<CalendarConnectionEntity> rows;
        rows = runAsTenant(tenantId, calendarConnectionRepository::findAll);
        assertThat(rows).as("exactly one calendar_connections row is persisted").hasSize(1);
        CalendarConnectionEntity persisted = rows.getFirst();
        assertThat(persisted.getStatus()).isEqualTo(CalendarConnectionStatus.CONNECTED);
        assertThat(persisted.getGoogleEmail()).isEqualTo(googleEmail);

        byte[] decrypted =
                oAuthTokenStore.decrypt(
                        persisted.getRefreshTokenEncrypted(),
                        tenantId,
                        RowDiscriminator.CALENDAR_CONNECTION);
        assertThat(new String(decrypted, StandardCharsets.UTF_8))
                .as(
                        "decrypted refresh token must equal the upstream OAuth2RefreshToken token"
                                + " value")
                .isEqualTo(FAKE_CALENDAR_REFRESH_TOKEN);
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

    private <T> T runAsTenant(UUID tenantId, java.util.function.Supplier<T> action) {
        java.util.concurrent.atomic.AtomicReference<T> holder =
                new java.util.concurrent.atomic.AtomicReference<>();
        TenantContext.runWith(tenantId, () -> holder.set(action.get()));
        return holder.get();
    }

    @SuppressWarnings("unused")
    private static List<String> emptyScopes() {
        return Collections.emptyList();
    }
}
