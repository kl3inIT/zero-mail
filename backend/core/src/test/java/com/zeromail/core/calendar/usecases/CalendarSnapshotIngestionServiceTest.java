package com.zeromail.core.calendar.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.zeromail.core.calendar.domain.MailboxCalendarRole;
import com.zeromail.core.calendar.gateway.CalendarApiClientFactory;
import com.zeromail.core.calendar.persistence.CalendarEntity;
import com.zeromail.core.calendar.persistence.CalendarRepository;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceEntity;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Plain JUnit + Mockito test for {@link CalendarSnapshotIngestionService} — the Google API client
 * is fully mocked, so no real network access happens (TESTING.md no-real-LLM/no-real-API rule).
 */
class CalendarSnapshotIngestionServiceTest {

    private CalendarApiClientFactory calendarApiClientFactory;
    private CalendarRepository calendarRepository;
    private MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository;
    private CalendarSnapshotIngestionService calendarSnapshotIngestionService;
    private Calendar calendarClient;
    private Calendar.CalendarList calendarListResource;
    private Calendar.CalendarList.List calendarListListRequest;

    @BeforeEach
    void setUp() throws Exception {
        calendarApiClientFactory = mock(CalendarApiClientFactory.class);
        calendarRepository = mock(CalendarRepository.class);
        mailboxCalendarPreferenceRepository = mock(MailboxCalendarPreferenceRepository.class);
        calendarSnapshotIngestionService =
                new CalendarSnapshotIngestionService(
                        calendarApiClientFactory,
                        calendarRepository,
                        mailboxCalendarPreferenceRepository);
        calendarClient = mock(Calendar.class);
        calendarListResource = mock(Calendar.CalendarList.class);
        calendarListListRequest = mock(Calendar.CalendarList.List.class);
        when(calendarClient.calendarList()).thenReturn(calendarListResource);
        when(calendarListResource.list()).thenReturn(calendarListListRequest);
        // Pre-stub findByCalendarConnectionIdAndExternalCalendarIdAndTenantId to return empty so
        // every upsertCalendar() takes the INSERT branch by default (overridden in the reconnect
        // test).
        lenient()
                .when(
                        calendarRepository
                                .findByCalendarConnectionIdAndExternalCalendarIdAndTenantId(
                                        any(UUID.class), any(String.class), any(UUID.class)))
                .thenReturn(Optional.empty());
        lenient()
                .when(calendarRepository.save(any(CalendarEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ingestSnapshot_insertsCalendarRowsAndPrimaryDefaultRoles() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        UUID activeMailboxId = UUID.randomUUID();
        when(calendarApiClientFactory.buildClientForCalendarConnection(
                        tenantId, calendarConnectionId))
                .thenReturn(calendarClient);
        when(calendarListListRequest.execute())
                .thenReturn(buildCalendarList(true, "primary-id", "secondary-id"));

        calendarSnapshotIngestionService.ingestSnapshot(
                tenantId, calendarConnectionId, activeMailboxId);

        // 2 calendar rows (primary + secondary).
        ArgumentCaptor<CalendarEntity> savedCalendars =
                ArgumentCaptor.forClass(CalendarEntity.class);
        verify(calendarRepository, org.mockito.Mockito.times(2)).save(savedCalendars.capture());
        assertThat(savedCalendars.getAllValues())
                .extracting(CalendarEntity::getExternalCalendarId)
                .containsExactlyInAnyOrder("primary-id", "secondary-id");
        assertThat(savedCalendars.getAllValues()).filteredOn(CalendarEntity::isPrimary).hasSize(1);

        // 3 preference rows seeded against the primary calendar + active mailbox.
        ArgumentCaptor<MailboxCalendarPreferenceEntity> savedPreferences =
                ArgumentCaptor.forClass(MailboxCalendarPreferenceEntity.class);
        verify(mailboxCalendarPreferenceRepository, org.mockito.Mockito.times(3))
                .save(savedPreferences.capture());
        assertThat(savedPreferences.getAllValues())
                .extracting(MailboxCalendarPreferenceEntity::getRole)
                .containsExactlyInAnyOrder(
                        MailboxCalendarRole.FREEBUSY,
                        MailboxCalendarRole.EVENT_WRITE,
                        MailboxCalendarRole.BRIEF_SOURCE);
        assertThat(savedPreferences.getAllValues())
                .allSatisfy(
                        preference -> {
                            assertThat(preference.getMailboxId()).isEqualTo(activeMailboxId);
                            assertThat(preference.getCalendarConnectionId())
                                    .isEqualTo(calendarConnectionId);
                        });
    }

    @Test
    void ingestSnapshot_skipsDefaultRolesIfActiveMailboxIsNull() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        when(calendarApiClientFactory.buildClientForCalendarConnection(
                        tenantId, calendarConnectionId))
                .thenReturn(calendarClient);
        when(calendarListListRequest.execute()).thenReturn(buildCalendarList(true, "primary-id"));

        calendarSnapshotIngestionService.ingestSnapshot(tenantId, calendarConnectionId, null);

        verify(calendarRepository).save(any(CalendarEntity.class));
        verify(mailboxCalendarPreferenceRepository, never()).save(any());
    }

    @Test
    void ingestSnapshot_handlesWorkspacePolicy403AndDoesNotThrow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        UUID activeMailboxId = UUID.randomUUID();
        when(calendarApiClientFactory.buildClientForCalendarConnection(
                        tenantId, calendarConnectionId))
                .thenReturn(calendarClient);
        // Build a GoogleJsonResponseException with status 403 (Workspace OU policy blocking
        // calendarList access — Pitfall 8).
        GoogleJsonResponseException workspaceBlocked =
                new GoogleJsonResponseException(
                        new HttpResponseException.Builder(403, "Forbidden", new HttpHeaders()),
                        null);
        when(calendarListListRequest.execute()).thenThrow(workspaceBlocked);

        // Must not throw — Pitfall 8 absorbed.
        calendarSnapshotIngestionService.ingestSnapshot(
                tenantId, calendarConnectionId, activeMailboxId);

        verify(calendarRepository, never()).save(any());
        verify(mailboxCalendarPreferenceRepository, never()).save(any());
    }

    @Test
    void ingestSnapshot_upsertsExistingCalendarRowOnReconnect() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        UUID activeMailboxId = UUID.randomUUID();
        when(calendarApiClientFactory.buildClientForCalendarConnection(
                        tenantId, calendarConnectionId))
                .thenReturn(calendarClient);
        when(calendarListListRequest.execute()).thenReturn(buildCalendarList(true, "primary-id"));

        UUID existingPrimaryRowId = UUID.randomUUID();
        CalendarEntity existingPrimary =
                new CalendarEntity(
                        existingPrimaryRowId,
                        tenantId,
                        calendarConnectionId,
                        "primary-id",
                        "Old Name",
                        true,
                        true);
        when(calendarRepository.findByCalendarConnectionIdAndExternalCalendarIdAndTenantId(
                        eq(calendarConnectionId), eq("primary-id"), eq(tenantId)))
                .thenReturn(Optional.of(existingPrimary));

        calendarSnapshotIngestionService.ingestSnapshot(
                tenantId, calendarConnectionId, activeMailboxId);

        // The existing row is updated in place — no fresh UUID was created for it.
        ArgumentCaptor<CalendarEntity> savedCalendars =
                ArgumentCaptor.forClass(CalendarEntity.class);
        verify(calendarRepository).save(savedCalendars.capture());
        assertThat(savedCalendars.getValue().getId()).isEqualTo(existingPrimaryRowId);
    }

    @Test
    void ingestSnapshot_propagatesNon403GoogleApiErrors() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID calendarConnectionId = UUID.randomUUID();
        when(calendarApiClientFactory.buildClientForCalendarConnection(
                        tenantId, calendarConnectionId))
                .thenReturn(calendarClient);
        GoogleJsonResponseException serverError =
                new GoogleJsonResponseException(
                        new HttpResponseException.Builder(500, "Internal", new HttpHeaders()),
                        null);
        when(calendarListListRequest.execute()).thenThrow(serverError);

        assertThatThrownBy(
                        () ->
                                calendarSnapshotIngestionService.ingestSnapshot(
                                        tenantId, calendarConnectionId, UUID.randomUUID()))
                .isInstanceOf(IOException.class);
    }

    private static CalendarList buildCalendarList(boolean firstIsPrimary, String... ids) {
        List<CalendarListEntry> entries = new ArrayList<>();
        for (int index = 0; index < ids.length; index++) {
            CalendarListEntry entry = new CalendarListEntry();
            entry.setId(ids[index]);
            entry.setSummary("Calendar " + ids[index]);
            entry.setTimeZone("UTC");
            if (firstIsPrimary && index == 0) {
                entry.setPrimary(true);
            }
            entries.add(entry);
        }
        CalendarList list = new CalendarList();
        list.setItems(entries);
        return list;
    }
}
