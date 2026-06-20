package com.zeromail.core.oauth.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Plain JUnit 5 unit test (TESTING.md Layer 1 — no Spring context) for the {@link GoogleOAuthScope}
 * ledger. Locks the invariants required by INFRA-01 (D-01 / D-02):
 *
 * <ul>
 *   <li>Every constant's {@code value()} URL is unique across the enum.
 *   <li>{@link GoogleOAuthScope#fromValue(String)} round-trips for every known constant.
 *   <li>Unknown URLs (e.g. any {@code drive*} scope) throw {@link NoSuchElementException}.
 *   <li>The three Calendar constants and {@code GMAIL_MODIFY} are present.
 *   <li>No constant has a URL starting with the Drive scope prefix (Phase 15 work).
 * </ul>
 */
class GoogleOAuthScopeEnumTest {

    @Test
    void every_value_is_unique_across_the_enum() {
        Set<String> uniqueValues =
                Arrays.stream(GoogleOAuthScope.values())
                        .map(GoogleOAuthScope::value)
                        .collect(Collectors.toSet());

        assertThat(uniqueValues).hasSize(GoogleOAuthScope.values().length);
    }

    @Test
    void from_value_round_trips_for_every_constant() {
        for (GoogleOAuthScope scope : GoogleOAuthScope.values()) {
            assertThat(GoogleOAuthScope.fromValue(scope.value())).isEqualTo(scope);
        }
    }

    @Test
    void from_value_fails_loud_on_unknown_drive_scope() {
        String googleApiPrefix = "https://" + "www.googleapis.com/auth/";

        assertThatThrownBy(() -> GoogleOAuthScope.fromValue(googleApiPrefix + "drive"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown Google OAuth scope:")
                .hasMessageContaining("drive");
    }

    @Test
    void calendar_and_gmail_modify_constants_are_present() {
        Set<String> namesPresent =
                Arrays.stream(GoogleOAuthScope.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet());

        assertThat(namesPresent)
                .contains(
                        "CALENDAR_FREEBUSY",
                        "CALENDAR_EVENTS",
                        "CALENDAR_READONLY",
                        "GMAIL_MODIFY");
    }

    @Test
    void no_constant_carries_any_drive_scope_url() {
        String drivePrefix = "https://" + "www.googleapis.com/auth/drive";

        for (GoogleOAuthScope scope : GoogleOAuthScope.values()) {
            assertThat(scope.value())
                    .as("constant %s must not carry a drive scope URL", scope.name())
                    .doesNotStartWith(drivePrefix);
        }
    }
}
