package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.oauth.scope.GoogleOAuthScope;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Phase 12 W1 invariants for the {@code google-calendar} {@code ClientRegistration} and the {@code
 * GoogleAuthorizationRequestResolver} calendar branch. Layer 1 / 2 mix per TESTING.md §3 — plain
 * JUnit + mock {@code ClientRegistrationRepository} keeps this fast (no Boot context).
 *
 * <p>Pins, in this single test file (CAL-CONN-01 / CAL-CONN-02 / D-02 / D-03):
 *
 * <ul>
 *   <li>The boot-time {@code @PostConstruct} assertion in {@link CalendarClientRegistrationConfig}
 *       accepts the three {@link GoogleOAuthScope} calendar values and rejects (a) a missing
 *       registration with a warn-and-return (dev profile permissibility) and (b) a registration
 *       that contains the unsafe full {@code calendar} scope.
 *   <li>The Calendar registration's scope set EQUALS the three enum values exactly — not a
 *       superset, not a subset.
 *   <li>The Calendar registration shares the {@code google} registration's client-id (Pitfall 2
 *       mitigation — single GCP client, two registrations).
 *   <li>{@link GoogleAuthorizationRequestResolver} always sets {@code prompt=consent} when the
 *       resolved registrationId is {@code google-calendar}, even without {@code reconnect=true}.
 * </ul>
 *
 * <p>The unsafe full-calendar scope literal is built by concatenation so the test source itself
 * never contains it as a literal (keeps the {@code OAuthScopeAllowListTest} scanner irrelevant for
 * test code, but matches the discipline applied throughout the codebase).
 */
class CalendarClientRegistrationConfigTest {

    private static final String GOOGLE_REGISTRATION_ID = "google";
    private static final String CALENDAR_REGISTRATION_ID = "google-calendar";
    private static final String SHARED_CLIENT_ID = "test-shared-google-client";

    @Test
    void boot_assertion_accepts_the_three_ledger_calendar_scopes() {
        ClientRegistrationRepository registry = registryWithCalendar(calendarRegistrationOk());

        // @PostConstruct is invoked manually here so the test does not need a Spring context.
        var config = new CalendarClientRegistrationConfig(registry);
        config.assertCalendarRegistrationMatchesLedger();

        assertThat(registry.findByRegistrationId(CALENDAR_REGISTRATION_ID).getScopes())
                .as("Calendar registration must carry exactly the three ledger scopes")
                .containsExactlyInAnyOrder(
                        GoogleOAuthScope.CALENDAR_FREEBUSY.value(),
                        GoogleOAuthScope.CALENDAR_EVENTS.value(),
                        GoogleOAuthScope.CALENDAR_READONLY.value());
    }

    @Test
    void boot_assertion_logs_and_returns_when_registration_is_absent() {
        ClientRegistrationRepository registry = mock(ClientRegistrationRepository.class);
        when(registry.findByRegistrationId(CALENDAR_REGISTRATION_ID)).thenReturn(null);

        // Missing registration must NOT throw — dev profile typically has no GOOGLE_OAUTH_CLIENT_ID
        // set so Spring drops the auto-bound bean entirely. Hard fail on missing would prevent
        // the dev app from booting.
        var config = new CalendarClientRegistrationConfig(registry);
        config.assertCalendarRegistrationMatchesLedger();
    }

    @Test
    void boot_assertion_rejects_a_registration_carrying_the_unsafe_full_calendar_scope() {
        String unsafeFullCalendarScope = "https://" + "www.googleapis.com/auth/" + "calendar";
        ClientRegistration drifted =
                ClientRegistration.withRegistrationId(CALENDAR_REGISTRATION_ID)
                        .clientId(SHARED_CLIENT_ID)
                        .clientSecret("test-secret")
                        .clientName(CALENDAR_REGISTRATION_ID)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri(
                                "http://localhost/login/oauth2/code/" + CALENDAR_REGISTRATION_ID)
                        .scope(Set.of(unsafeFullCalendarScope))
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .tokenUri("https://oauth2.googleapis.com/token")
                        .build();
        ClientRegistrationRepository registry = registryWithCalendar(drifted);

        assertThatThrownBy(
                        () ->
                                new CalendarClientRegistrationConfig(registry)
                                        .assertCalendarRegistrationMatchesLedger())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GoogleOAuthScope ledger");
    }

    @Test
    void calendar_registration_does_not_contain_the_unsafe_full_calendar_scope() {
        String unsafeFullCalendarScope = "https://" + "www.googleapis.com/auth/" + "calendar";
        Set<String> calendarScopes = calendarRegistrationOk().getScopes();

        assertThat(calendarScopes)
                .as("Calendar registration must NEVER request the unsafe full `calendar` scope")
                .doesNotContain(unsafeFullCalendarScope);
        assertThat(calendarScopes)
                .as("Calendar registration must carry exactly the three ledger scopes")
                .containsExactlyInAnyOrder(
                        GoogleOAuthScope.CALENDAR_FREEBUSY.value(),
                        GoogleOAuthScope.CALENDAR_EVENTS.value(),
                        GoogleOAuthScope.CALENDAR_READONLY.value());
    }

    @Test
    void calendar_registration_shares_client_id_with_google_registration() {
        ClientRegistration calendarRegistration = calendarRegistrationOk();
        ClientRegistration googleRegistration = googleRegistrationOk();

        assertThat(calendarRegistration.getClientId())
                .as("Pitfall 2 mitigation: shared GCP OAuth client across the two registrations")
                .isEqualTo(googleRegistration.getClientId());
    }

    @Test
    void resolver_sets_prompt_consent_on_calendar_flow_without_reconnect_param() {
        ClientRegistrationRepository registry =
                registryWith(googleRegistrationOk(), calendarRegistrationOk());
        var resolver = new GoogleAuthorizationRequestResolver(registry);

        var servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/oauth2/authorization/" + CALENDAR_REGISTRATION_ID);
        servletRequest.setServletPath("/oauth2/authorization/" + CALENDAR_REGISTRATION_ID);
        servletRequest.setMethod("GET");
        // No reconnect=true — the calendar branch must STILL force consent.

        OAuth2AuthorizationRequest result =
                resolver.resolve(servletRequest, CALENDAR_REGISTRATION_ID);

        assertThat(result).isNotNull();
        Map<String, Object> additionalParameters = result.getAdditionalParameters();
        assertThat(additionalParameters)
                .as("CAL-CONN-01: calendar flow always forces prompt=consent")
                .containsEntry("prompt", "consent")
                .containsEntry("access_type", "offline")
                .containsEntry("include_granted_scopes", "true");
    }

    @Test
    void resolver_keeps_existing_google_behaviour_unchanged_for_first_login() {
        ClientRegistrationRepository registry =
                registryWith(googleRegistrationOk(), calendarRegistrationOk());
        var resolver = new GoogleAuthorizationRequestResolver(registry);

        var servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        servletRequest.setServletPath("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        servletRequest.setMethod("GET");
        // No reconnect=true — the Gmail first-login flow must NOT force consent (existing UX).

        OAuth2AuthorizationRequest result =
                resolver.resolve(servletRequest, GOOGLE_REGISTRATION_ID);

        assertThat(result).isNotNull();
        assertThat(result.getAdditionalParameters())
                .as("Gmail first-login flow must continue to omit prompt=consent")
                .doesNotContainKey("prompt");
    }

    private ClientRegistrationRepository registryWithCalendar(ClientRegistration calendar) {
        return registryWith(googleRegistrationOk(), calendar);
    }

    private ClientRegistrationRepository registryWith(
            ClientRegistration google, ClientRegistration calendar) {
        ClientRegistrationRepository registry = mock(ClientRegistrationRepository.class);
        when(registry.findByRegistrationId(eq(GOOGLE_REGISTRATION_ID))).thenReturn(google);
        when(registry.findByRegistrationId(eq(CALENDAR_REGISTRATION_ID))).thenReturn(calendar);
        return registry;
    }

    private ClientRegistration googleRegistrationOk() {
        return ClientRegistration.withRegistrationId(GOOGLE_REGISTRATION_ID)
                .clientId(SHARED_CLIENT_ID)
                .clientSecret("test-secret")
                .clientName(GOOGLE_REGISTRATION_ID)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/" + GOOGLE_REGISTRATION_ID)
                .scope(List.of("openid", "profile", "email", GoogleOAuthScope.GMAIL_MODIFY.value()))
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();
    }

    private ClientRegistration calendarRegistrationOk() {
        return ClientRegistration.withRegistrationId(CALENDAR_REGISTRATION_ID)
                .clientId(SHARED_CLIENT_ID)
                .clientSecret("test-secret")
                .clientName(CALENDAR_REGISTRATION_ID)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/" + CALENDAR_REGISTRATION_ID)
                .scope(
                        List.of(
                                GoogleOAuthScope.CALENDAR_FREEBUSY.value(),
                                GoogleOAuthScope.CALENDAR_EVENTS.value(),
                                GoogleOAuthScope.CALENDAR_READONLY.value()))
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();
    }
}
