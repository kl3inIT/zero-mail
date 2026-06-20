package com.zeromail.api.security;

import com.zeromail.core.oauth.scope.GoogleOAuthScope;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Boot-time sanity gate for the {@code google-calendar} OAuth2 {@code ClientRegistration} (Phase 12
 * W1, CAL-CONN-01 / CAL-CONN-02 / D-02).
 *
 * <p>The registration itself is auto-bound by Spring's OAuth2 Client starter from the {@code
 * spring.security.oauth2.client.registration.google-calendar.*} block in {@code application.yml} —
 * this {@code @Configuration} owns no {@code @Bean}, only a {@code @PostConstruct} assertion that
 * cross-checks the bound bean against the {@link GoogleOAuthScope} ledger so a typo in the YAML
 * (extra full {@code calendar} scope, missing {@code calendar.freebusy}, swapped URL host) fails
 * fast at app boot instead of silently sending the wrong consent screen to a user.
 *
 * <p>The assertion reads scope URLs via {@link GoogleOAuthScope#value()} — never a literal — so
 * {@code OAuthScopeAllowListTest}'s source-text scanner stays green for this file (D-03 invariant).
 *
 * <p>Per CONVENTIONS §5 privacy logging: the log line carries only the opaque registration id, not
 * the bound client-id, client-secret, or scope URLs.
 */
@Configuration
public class CalendarClientRegistrationConfig {

    private static final Logger log =
            LoggerFactory.getLogger(CalendarClientRegistrationConfig.class);

    static final String CALENDAR_REGISTRATION_ID = "google-calendar";

    private final ClientRegistrationRepository clientRegistrationRepository;

    public CalendarClientRegistrationConfig(
            ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @PostConstruct
    void assertCalendarRegistrationMatchesLedger() {
        ClientRegistration calendarRegistration =
                clientRegistrationRepository.findByRegistrationId(CALENDAR_REGISTRATION_ID);
        if (calendarRegistration == null) {
            // OAuth2 client autoconfiguration silently drops a registration with an empty
            // client-id (dev profile when GOOGLE_OAUTH_CLIENT_ID env var is unset). Surface
            // this gracefully — log a warning rather than failing boot, because most local
            // development does not exercise the Calendar OAuth flow.
            log.warn(
                    "event=calendar_registration_missing registrationId={}",
                    CALENDAR_REGISTRATION_ID);
            return;
        }

        Set<String> expectedScopes =
                Set.of(
                        GoogleOAuthScope.CALENDAR_FREEBUSY.value(),
                        GoogleOAuthScope.CALENDAR_EVENTS.value(),
                        GoogleOAuthScope.CALENDAR_READONLY.value());
        Set<String> actualScopes = Set.copyOf(calendarRegistration.getScopes());

        if (!expectedScopes.equals(actualScopes)) {
            // Asserting at boot prevents a misconfigured prod from sending the wrong consent
            // screen to a user. Hard fail — this is a configuration bug, not a transient error.
            throw new IllegalStateException(
                    "Calendar registration scopes do not match GoogleOAuthScope ledger. "
                            + "Expected exactly the three CALENDAR_* enum values; verify "
                            + "application.yml against the enum.");
        }

        log.info(
                "event=calendar_registration_verified registrationId={}", CALENDAR_REGISTRATION_ID);
    }
}
