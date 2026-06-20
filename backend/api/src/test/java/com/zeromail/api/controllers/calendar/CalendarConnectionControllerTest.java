package com.zeromail.api.controllers.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.zeromail.api.dto.calendar.CalendarConnectionResponse;
import com.zeromail.api.dto.calendar.CalendarToggleResponse;
import com.zeromail.api.dto.calendar.MailboxCalendarPreferenceResponse;
import com.zeromail.api.dto.calendar.UpdateCalendarEnabledRequest;
import com.zeromail.api.dto.calendar.UpdateMailboxCalendarPreferenceRequest;
import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.calendar.domain.MailboxCalendarRole;
import com.zeromail.core.calendar.exception.CalendarConnectionNotOwnedException;
import com.zeromail.core.calendar.projection.CalendarConnectionView;
import com.zeromail.core.calendar.projection.CalendarConnectionView.CalendarView;
import com.zeromail.core.calendar.projection.MailboxCalendarPreferenceView;
import com.zeromail.core.calendar.usecases.CalendarConnectionService;
import com.zeromail.core.calendar.usecases.CalendarToggleService;
import com.zeromail.core.calendar.usecases.MailboxCalendarPreferenceService;
import com.zeromail.core.calendar.usecases.UpdateMailboxCalendarPreferenceCommand;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Plain-unit (no Spring context) tests for the Calendar REST surface. Controllers are thin per
 * CONVENTIONS §1 — services are mocked, TenantContext is bound via {@code ScopedValue.where}, and
 * the test asserts only (a) the controller calls the service with the right arguments and (b) the
 * service result is mapped into the right response shape. Wire-format defaults ({@code @Schema},
 * {@code @JsonInclude(NON_NULL)}) are exercised by the springdoc OpenAPI emit task at build time
 * (separate gate).
 */
class CalendarConnectionControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");
    private static final UUID MAILBOX_ID = UUID.fromString("00000000-0000-0000-0000-00000000bbbb");
    private static final Instant NOW = Instant.parse("2026-06-20T10:00:00Z");

    private final CalendarConnectionService calendarConnectionService =
            mock(CalendarConnectionService.class);
    private final CalendarToggleService calendarToggleService = mock(CalendarToggleService.class);
    private final MailboxCalendarPreferenceService mailboxCalendarPreferenceService =
            mock(MailboxCalendarPreferenceService.class);

    @Test
    void listConnectionsForMailbox_returnsMappedConnectionsForCurrentTenant() {
        CalendarConnectionController controller =
                new CalendarConnectionController(calendarConnectionService, calendarToggleService);
        UUID calendarConnectionId = UUID.fromString("00000000-0000-0000-0000-00000000cccc");
        UUID calendarId = UUID.fromString("00000000-0000-0000-0000-00000000dddd");
        CalendarView calendarView =
                new CalendarView(calendarId, "ext-id-1", "Primary", true, true, "UTC");
        MailboxCalendarPreferenceView preferenceView =
                new MailboxCalendarPreferenceView(
                        UUID.fromString("00000000-0000-0000-0000-00000000eeee"),
                        MAILBOX_ID,
                        calendarConnectionId,
                        calendarId,
                        MailboxCalendarRole.FREEBUSY);
        CalendarConnectionView view =
                new CalendarConnectionView(
                        calendarConnectionId,
                        TENANT_ID,
                        "user@example.test",
                        CalendarConnectionStatus.CONNECTED,
                        NOW,
                        null,
                        null,
                        null,
                        List.of(calendarView),
                        List.of(preferenceView));
        given(calendarConnectionService.list(TENANT_ID, MAILBOX_ID)).willReturn(List.of(view));

        List<CalendarConnectionResponse> response =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(() -> controller.listConnectionsForMailbox(MAILBOX_ID));

        assertThat(response).hasSize(1);
        CalendarConnectionResponse responseRow = response.get(0);
        assertThat(responseRow.id()).isEqualTo(calendarConnectionId.toString());
        assertThat(responseRow.googleEmail()).isEqualTo("user@example.test");
        assertThat(responseRow.status()).isEqualTo("CONNECTED");
        assertThat(responseRow.calendars()).hasSize(1);
        assertThat(responseRow.calendars().get(0).isPrimary()).isTrue();
        assertThat(responseRow.preferences()).hasSize(1);
        assertThat(responseRow.preferences().get(0).role()).isEqualTo("FREEBUSY");
    }

    @Test
    void disconnect_callsServiceWithCurrentTenant() {
        CalendarConnectionController controller =
                new CalendarConnectionController(calendarConnectionService, calendarToggleService);
        UUID calendarConnectionId = UUID.fromString("00000000-0000-0000-0000-00000000ffff");

        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .run(() -> controller.disconnect(calendarConnectionId));

        then(calendarConnectionService).should().disconnect(TENANT_ID, calendarConnectionId);
    }

    @Test
    void disconnect_propagatesNotOwnedExceptionForCrossTenantAccess() {
        CalendarConnectionController controller =
                new CalendarConnectionController(calendarConnectionService, calendarToggleService);
        UUID calendarConnectionId = UUID.fromString("00000000-0000-0000-0000-000000000fff");
        org.mockito.Mockito.doThrow(
                        new CalendarConnectionNotOwnedException(TENANT_ID, calendarConnectionId))
                .when(calendarConnectionService)
                .disconnect(TENANT_ID, calendarConnectionId);

        assertThatExceptionOfType(CalendarConnectionNotOwnedException.class)
                .isThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                                        .run(() -> controller.disconnect(calendarConnectionId)));
    }

    @Test
    void toggleEnabled_returnsCascadeDeleteCount() {
        CalendarConnectionController controller =
                new CalendarConnectionController(calendarConnectionService, calendarToggleService);
        UUID calendarId = UUID.fromString("00000000-0000-0000-0000-0000000aaaaa");
        given(calendarToggleService.setEnabled(TENANT_ID, calendarId, false)).willReturn(3);

        CalendarToggleResponse response =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(
                                () ->
                                        controller.toggleEnabled(
                                                calendarId,
                                                new UpdateCalendarEnabledRequest(false)));

        assertThat(response.preferencesRemoved()).isEqualTo(3);
    }

    @Test
    void preferencesController_list_returnsMappedRows() {
        MailboxCalendarPreferenceController preferencesController =
                new MailboxCalendarPreferenceController(mailboxCalendarPreferenceService);
        UUID calendarConnectionId = UUID.fromString("00000000-0000-0000-0000-0000000bbbbb");
        UUID calendarId = UUID.fromString("00000000-0000-0000-0000-0000000ccccc");
        MailboxCalendarPreferenceView view =
                new MailboxCalendarPreferenceView(
                        UUID.fromString("00000000-0000-0000-0000-0000000ddddd"),
                        MAILBOX_ID,
                        calendarConnectionId,
                        calendarId,
                        MailboxCalendarRole.EVENT_WRITE);
        given(mailboxCalendarPreferenceService.findAllForMailbox(TENANT_ID, MAILBOX_ID))
                .willReturn(List.of(view));

        List<MailboxCalendarPreferenceResponse> response =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(() -> preferencesController.list(MAILBOX_ID));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).role()).isEqualTo("EVENT_WRITE");
    }

    @Test
    void preferencesController_update_buildsCommandAndDelegatesToService() {
        MailboxCalendarPreferenceController preferencesController =
                new MailboxCalendarPreferenceController(mailboxCalendarPreferenceService);
        UUID freebusyCalendarId = UUID.fromString("00000000-0000-0000-0000-0000000eeeee");
        UUID eventWriteCalendarId = UUID.fromString("00000000-0000-0000-0000-0000000fffff");
        UpdateMailboxCalendarPreferenceRequest request =
                new UpdateMailboxCalendarPreferenceRequest(
                        List.of(freebusyCalendarId), eventWriteCalendarId, null);
        given(
                        mailboxCalendarPreferenceService.updateForMailbox(
                                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                                org.mockito.ArgumentMatchers.any(
                                        UpdateMailboxCalendarPreferenceCommand.class)))
                .willReturn(List.of());

        ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                .call(() -> preferencesController.update(MAILBOX_ID, request));

        ArgumentCaptor<UpdateMailboxCalendarPreferenceCommand> commandCaptor =
                ArgumentCaptor.forClass(UpdateMailboxCalendarPreferenceCommand.class);
        then(mailboxCalendarPreferenceService)
                .should()
                .updateForMailbox(
                        org.mockito.ArgumentMatchers.eq(TENANT_ID), commandCaptor.capture());
        UpdateMailboxCalendarPreferenceCommand capturedCommand = commandCaptor.getValue();
        assertThat(capturedCommand.mailboxId()).isEqualTo(MAILBOX_ID);
        assertThat(capturedCommand.freebusyCalendarIds()).containsExactly(freebusyCalendarId);
        assertThat(capturedCommand.eventWriteCalendarId()).isEqualTo(eventWriteCalendarId);
        assertThat(capturedCommand.briefSourceCalendarId()).isNull();
    }
}
