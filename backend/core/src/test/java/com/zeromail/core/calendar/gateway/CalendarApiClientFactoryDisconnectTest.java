package com.zeromail.core.calendar.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.calendar.exception.CalendarConnectionNotOwnedException;
import com.zeromail.core.calendar.exception.CalendarDisconnectedException;
import com.zeromail.core.calendar.persistence.CalendarConnectionEntity;
import com.zeromail.core.calendar.persistence.CalendarConnectionRepository;
import com.zeromail.core.oauth.token.OAuthTokenStore;
import com.zeromail.core.oauth.token.OAuthTokenStore.RowDiscriminator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Plain JUnit (TESTING.md §3 Layer 1 — no Spring context) gate for the factory's fail-fast
 * lifecycle behaviour: every non-CONNECTED status surfaces {@link CalendarDisconnectedException}; a
 * missing or cross-tenant row surfaces {@link CalendarConnectionNotOwnedException}; and {@link
 * CalendarApiClientFactory#evictAccessToken(UUID)} short-circuits the cipher path on subsequent
 * calls (no leak of decrypted refresh tokens past disconnect).
 */
class CalendarApiClientFactoryDisconnectTest {

    private static final String CLIENT_ID = "test-google-client";
    private static final String CLIENT_SECRET = "test-google-secret";

    @Test
    void buildClient_throws_when_row_status_is_disconnected() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionRepository calendarConnectionRepository =
                mock(CalendarConnectionRepository.class);
        OAuthTokenStore oAuthTokenStore = mock(OAuthTokenStore.class);

        CalendarConnectionEntity disconnectedRow =
                new CalendarConnectionEntity(
                        calendarConnectionId,
                        tenantId,
                        "user@example.test",
                        CalendarConnectionStatus.DISCONNECTED);
        when(calendarConnectionRepository.findByIdAndTenantId(calendarConnectionId, tenantId))
                .thenReturn(Optional.of(disconnectedRow));

        CalendarApiClientFactory factory =
                new CalendarApiClientFactory(
                        CLIENT_ID, CLIENT_SECRET, calendarConnectionRepository, oAuthTokenStore);

        assertThatThrownBy(
                        () ->
                                factory.buildClientForCalendarConnection(
                                        tenantId, calendarConnectionId))
                .isInstanceOf(CalendarDisconnectedException.class)
                .satisfies(
                        thrown ->
                                assertThat(((CalendarDisconnectedException) thrown).status())
                                        .as("the offending status is carried on the exception")
                                        .isEqualTo(CalendarConnectionStatus.DISCONNECTED));

        verify(oAuthTokenStore, never()).decrypt(any(), any(), any(RowDiscriminator.class));
    }

    @Test
    void buildClient_throws_when_row_status_is_revoked() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionRepository calendarConnectionRepository =
                mock(CalendarConnectionRepository.class);
        OAuthTokenStore oAuthTokenStore = mock(OAuthTokenStore.class);

        CalendarConnectionEntity revokedRow =
                new CalendarConnectionEntity(
                        calendarConnectionId,
                        tenantId,
                        "user@example.test",
                        CalendarConnectionStatus.REVOKED);
        when(calendarConnectionRepository.findByIdAndTenantId(calendarConnectionId, tenantId))
                .thenReturn(Optional.of(revokedRow));

        CalendarApiClientFactory factory =
                new CalendarApiClientFactory(
                        CLIENT_ID, CLIENT_SECRET, calendarConnectionRepository, oAuthTokenStore);

        assertThatThrownBy(
                        () ->
                                factory.buildClientForCalendarConnection(
                                        tenantId, calendarConnectionId))
                .isInstanceOf(CalendarDisconnectedException.class)
                .satisfies(
                        thrown ->
                                assertThat(((CalendarDisconnectedException) thrown).status())
                                        .isEqualTo(CalendarConnectionStatus.REVOKED));
    }

    @Test
    void buildClient_throws_not_owned_when_repository_returns_empty() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionRepository calendarConnectionRepository =
                mock(CalendarConnectionRepository.class);
        OAuthTokenStore oAuthTokenStore = mock(OAuthTokenStore.class);

        when(calendarConnectionRepository.findByIdAndTenantId(calendarConnectionId, tenantId))
                .thenReturn(Optional.empty());

        CalendarApiClientFactory factory =
                new CalendarApiClientFactory(
                        CLIENT_ID, CLIENT_SECRET, calendarConnectionRepository, oAuthTokenStore);

        assertThatThrownBy(
                        () ->
                                factory.buildClientForCalendarConnection(
                                        tenantId, calendarConnectionId))
                .isInstanceOf(CalendarConnectionNotOwnedException.class);
    }

    @Test
    void buildClient_throws_disconnected_when_refresh_token_envelope_is_missing() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionRepository calendarConnectionRepository =
                mock(CalendarConnectionRepository.class);
        OAuthTokenStore oAuthTokenStore = mock(OAuthTokenStore.class);

        // status=CONNECTED but no refresh-token envelope — surface as disconnected so the
        // controller maps to 409 instead of leaking a 500.
        CalendarConnectionEntity emptyEnvelopeRow =
                new CalendarConnectionEntity(
                        calendarConnectionId,
                        tenantId,
                        "user@example.test",
                        CalendarConnectionStatus.CONNECTED);
        emptyEnvelopeRow.setRefreshTokenEncrypted(new byte[0]);
        when(calendarConnectionRepository.findByIdAndTenantId(calendarConnectionId, tenantId))
                .thenReturn(Optional.of(emptyEnvelopeRow));

        CalendarApiClientFactory factory =
                new CalendarApiClientFactory(
                        CLIENT_ID, CLIENT_SECRET, calendarConnectionRepository, oAuthTokenStore);

        assertThatThrownBy(
                        () ->
                                factory.buildClientForCalendarConnection(
                                        tenantId, calendarConnectionId))
                .isInstanceOf(CalendarDisconnectedException.class);
    }

    @Test
    void evictAccessToken_does_not_consult_the_repository() {
        // Pure cache-eviction path — no DB read should happen. The test doubles as a
        // documentation guard for W2's disconnect path: evict THEN flip status THEN publish event.
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionRepository calendarConnectionRepository =
                mock(CalendarConnectionRepository.class);
        OAuthTokenStore oAuthTokenStore = mock(OAuthTokenStore.class);
        lenient()
                .when(
                        calendarConnectionRepository.findByIdAndTenantId(
                                eq(calendarConnectionId), any()))
                .thenReturn(Optional.empty());

        CalendarApiClientFactory factory =
                new CalendarApiClientFactory(
                        CLIENT_ID, CLIENT_SECRET, calendarConnectionRepository, oAuthTokenStore);

        factory.evictAccessToken(calendarConnectionId);

        verify(calendarConnectionRepository, never()).findByIdAndTenantId(any(), any());
    }
}
