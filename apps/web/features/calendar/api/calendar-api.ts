import { api } from '@/lib/api/client';

import type {
  CalendarConnection,
  CalendarToggleResponse,
  MailboxCalendarPreference,
  UpdateMailboxCalendarPreferenceRequest,
  CalendarConnectIntentResponse,
} from '@/features/calendar/types';

// Phase 12 G1: promoted from raw `fetch` + manual CSRF header plumbing to the typed openapi-fetch
// client.
// The `api` client's onRequest middleware echoes the Spring `XSRF-TOKEN` cookie automatically for
// mutating methods, so the explicit header plumbing is gone. Path params + request/response bodies
// are type-checked against the generated `paths` map.

export async function listCalendarConnections(mailboxId: string): Promise<CalendarConnection[]> {
  const { data, error, response } = await api.GET(
    '/api/calendar/mailboxes/{mailboxId}/connections',
    { params: { path: { mailboxId } } },
  );
  if (error || !response.ok || data === undefined) {
    throw (
      error ??
      new Error(`/api/calendar/mailboxes/${mailboxId}/connections failed: ${response.status}`)
    );
  }
  return data;
}

export async function disconnectCalendarConnection(calendarConnectionId: string): Promise<void> {
  const { error, response } = await api.DELETE('/api/calendar/connections/{calendarConnectionId}', {
    params: { path: { calendarConnectionId } },
  });
  if (error || !response.ok) {
    throw (
      error ??
      new Error(
        `/api/calendar/connections/${calendarConnectionId} DELETE failed: ${response.status}`,
      )
    );
  }
}

export async function toggleCalendar(
  calendarId: string,
  enabled: boolean,
): Promise<CalendarToggleResponse> {
  const { data, error, response } = await api.PATCH(
    '/api/calendar/calendars/{calendarId}/enabled',
    {
      params: { path: { calendarId } },
      body: { enabled },
    },
  );
  if (error || !response.ok || data === undefined) {
    throw (
      error ??
      new Error(`/api/calendar/calendars/${calendarId}/enabled PATCH failed: ${response.status}`)
    );
  }
  return data;
}

export async function updateMailboxCalendarPreference(
  mailboxId: string,
  request: UpdateMailboxCalendarPreferenceRequest,
): Promise<MailboxCalendarPreference[]> {
  const { data, error, response } = await api.PATCH(
    '/api/calendar/mailboxes/{mailboxId}/preferences',
    { params: { path: { mailboxId } }, body: request },
  );
  if (error || !response.ok || data === undefined) {
    throw (
      error ??
      new Error(`/api/calendar/mailboxes/${mailboxId}/preferences PATCH failed: ${response.status}`)
    );
  }
  return data;
}

export async function prepareCalendarConnect(
  mailboxId: string,
): Promise<CalendarConnectIntentResponse> {
  const { data, error, response } = await api.POST('/api/calendar/connect-intent', {
    body: { mailboxId },
  });
  if (error || !response.ok || data === undefined) {
    throw error ?? new Error(`/api/calendar/connect-intent POST failed: ${response.status}`);
  }
  return data;
}
