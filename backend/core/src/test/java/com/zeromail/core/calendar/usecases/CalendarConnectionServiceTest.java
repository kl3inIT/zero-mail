package com.zeromail.core.calendar.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.calendar.domain.MailboxCalendarRole;
import com.zeromail.core.calendar.event.CalendarConnectionDisconnected;
import com.zeromail.core.calendar.exception.CalendarConnectionNotOwnedException;
import com.zeromail.core.calendar.gateway.CalendarApiClientFactory;
import com.zeromail.core.calendar.persistence.CalendarConnectionEntity;
import com.zeromail.core.calendar.persistence.CalendarConnectionRepository;
import com.zeromail.core.calendar.persistence.CalendarEntity;
import com.zeromail.core.calendar.persistence.CalendarRepository;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceEntity;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceRepository;
import com.zeromail.core.calendar.projection.CalendarConnectionView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Plain JUnit + Mockito gate for {@link CalendarConnectionService} (TESTING.md §3 Layer 1 — no
 * Spring context). Pins the W2 contract:
 *
 * <ul>
 *   <li>{@code list(...)} joins connection → sub-calendar → mailbox preference rows correctly.
 *   <li>{@code disconnect(...)} marks the row DISCONNECTED, clears the encrypted refresh token,
 *       cascades preference deletes, and publishes exactly one {@link
 *       CalendarConnectionDisconnected} event AFTER the transaction commits (verified by stubbing
 *       the {@link PlatformTransactionManager} to commit synchronously and asserting publication
 *       ordering).
 *   <li>Disconnect is idempotent — calling it on an already-DISCONNECTED row does not publish a
 *       second event.
 *   <li>{@code resolveOwnedConnectionOrThrow} throws {@link CalendarConnectionNotOwnedException}
 *       for cross-tenant lookups.
 * </ul>
 */
class CalendarConnectionServiceTest {

    private CalendarConnectionRepository calendarConnectionRepository;
    private CalendarRepository calendarRepository;
    private MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository;
    private ApplicationEventPublisher applicationEventPublisher;
    private CalendarApiClientFactory calendarApiClientFactory;
    private PlatformTransactionManager transactionManager;
    private CalendarConnectionService calendarConnectionService;

    @BeforeEach
    void setUp() {
        calendarConnectionRepository = mock(CalendarConnectionRepository.class);
        calendarRepository = mock(CalendarRepository.class);
        mailboxCalendarPreferenceRepository = mock(MailboxCalendarPreferenceRepository.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        calendarApiClientFactory = mock(CalendarApiClientFactory.class);
        transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        calendarConnectionService =
                new CalendarConnectionService(
                        calendarConnectionRepository,
                        calendarRepository,
                        mailboxCalendarPreferenceRepository,
                        applicationEventPublisher,
                        calendarApiClientFactory,
                        transactionManager);
    }

    @Test
    void list_joinsConnectionsSubCalendarsAndPreferences() {
        UUID tenantId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionEntity calendarConnection =
                new CalendarConnectionEntity(
                        calendarConnectionId,
                        tenantId,
                        "user@example.test",
                        CalendarConnectionStatus.CONNECTED);
        calendarConnection.setConnectedAt(Instant.now());
        when(calendarConnectionRepository.findAllByTenantId(tenantId))
                .thenReturn(List.of(calendarConnection));
        UUID primaryCalendarId = UUID.randomUUID();
        CalendarEntity primaryCalendar =
                new CalendarEntity(
                        primaryCalendarId,
                        tenantId,
                        calendarConnectionId,
                        "user@example.test",
                        "Primary",
                        true,
                        true);
        when(calendarRepository.findAllByCalendarConnectionIdAndTenantId(
                        calendarConnectionId, tenantId))
                .thenReturn(List.of(primaryCalendar));
        UUID preferenceRowId = UUID.randomUUID();
        MailboxCalendarPreferenceEntity preferenceRow =
                new MailboxCalendarPreferenceEntity(
                        preferenceRowId,
                        tenantId,
                        mailboxId,
                        calendarConnectionId,
                        primaryCalendarId,
                        MailboxCalendarRole.FREEBUSY);
        when(mailboxCalendarPreferenceRepository.findAllByMailboxIdAndTenantId(mailboxId, tenantId))
                .thenReturn(List.of(preferenceRow));

        List<CalendarConnectionView> connectionViews =
                calendarConnectionService.list(tenantId, mailboxId);

        assertThat(connectionViews).hasSize(1);
        CalendarConnectionView view = connectionViews.get(0);
        assertThat(view.id()).isEqualTo(calendarConnectionId);
        assertThat(view.status()).isEqualTo(CalendarConnectionStatus.CONNECTED);
        assertThat(view.calendars()).hasSize(1);
        assertThat(view.calendars().get(0).isPrimary()).isTrue();
        assertThat(view.mailboxPreferences()).hasSize(1);
        assertThat(view.mailboxPreferences().get(0).role()).isEqualTo(MailboxCalendarRole.FREEBUSY);
    }

    @Test
    void list_returnsEmptyListWhenNoConnectionsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();
        when(calendarConnectionRepository.findAllByTenantId(tenantId)).thenReturn(List.of());

        List<CalendarConnectionView> connectionViews =
                calendarConnectionService.list(tenantId, mailboxId);

        assertThat(connectionViews).isEmpty();
        verify(mailboxCalendarPreferenceRepository, never())
                .findAllByMailboxIdAndTenantId(any(), any());
    }

    @Test
    void disconnect_marksDisconnectedClearsTokenCascadesPreferencesAndPublishesEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionEntity connectedRow =
                new CalendarConnectionEntity(
                        calendarConnectionId,
                        tenantId,
                        "user@example.test",
                        CalendarConnectionStatus.CONNECTED);
        connectedRow.setRefreshTokenEncrypted(new byte[] {1, 2, 3, 4});
        when(calendarConnectionRepository.findByIdAndTenantId(calendarConnectionId, tenantId))
                .thenReturn(Optional.of(connectedRow));
        when(mailboxCalendarPreferenceRepository.deleteByCalendarConnectionIdAndTenantId(
                        calendarConnectionId, tenantId))
                .thenReturn(2);

        calendarConnectionService.disconnect(tenantId, calendarConnectionId);

        assertThat(connectedRow.getStatus()).isEqualTo(CalendarConnectionStatus.DISCONNECTED);
        assertThat(connectedRow.getDisconnectedAt()).isNotNull();
        assertThat(connectedRow.getRefreshTokenEncrypted()).isNull();
        verify(mailboxCalendarPreferenceRepository)
                .deleteByCalendarConnectionIdAndTenantId(calendarConnectionId, tenantId);
        verify(calendarConnectionRepository).save(connectedRow);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        Object captured = eventCaptor.getValue();
        assertThat(captured).isInstanceOf(CalendarConnectionDisconnected.class);
        CalendarConnectionDisconnected event = (CalendarConnectionDisconnected) captured;
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.calendarConnectionId()).isEqualTo(calendarConnectionId);
        assertThat(event.disconnectedAt()).isNotNull();
    }

    @Test
    void disconnect_isIdempotentWhenAlreadyDisconnected() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionEntity disconnectedRow =
                new CalendarConnectionEntity(
                        calendarConnectionId,
                        tenantId,
                        "user@example.test",
                        CalendarConnectionStatus.DISCONNECTED);
        when(calendarConnectionRepository.findByIdAndTenantId(calendarConnectionId, tenantId))
                .thenReturn(Optional.of(disconnectedRow));

        calendarConnectionService.disconnect(tenantId, calendarConnectionId);

        verify(mailboxCalendarPreferenceRepository, never())
                .deleteByCalendarConnectionIdAndTenantId(any(), any());
        verify(calendarConnectionRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void disconnect_throwsNotOwnedWhenRepositoryReturnsEmpty() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        when(calendarConnectionRepository.findByIdAndTenantId(calendarConnectionId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> calendarConnectionService.disconnect(tenantId, calendarConnectionId))
                .isInstanceOf(CalendarConnectionNotOwnedException.class);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void resolveOwnedConnectionOrThrow_throwsForCrossTenantAccess() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        when(calendarConnectionRepository.findByIdAndTenantId(calendarConnectionId, tenantB))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                calendarConnectionService.resolveOwnedConnectionOrThrow(
                                        tenantB, calendarConnectionId))
                .isInstanceOf(CalendarConnectionNotOwnedException.class)
                .satisfies(
                        thrown -> {
                            CalendarConnectionNotOwnedException notOwned =
                                    (CalendarConnectionNotOwnedException) thrown;
                            assertThat(notOwned.tenantId()).isEqualTo(tenantB);
                            assertThat(notOwned.calendarConnectionId())
                                    .isEqualTo(calendarConnectionId);
                        });
    }

    @Test
    void onCalendarConnectionDisconnected_evictsAccessTokenCache() {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        CalendarConnectionDisconnected event =
                new CalendarConnectionDisconnected(tenantId, calendarConnectionId, Instant.now());

        calendarConnectionService.onCalendarConnectionDisconnected(event);

        verify(calendarApiClientFactory).evictAccessToken(eq(calendarConnectionId));
    }
}
