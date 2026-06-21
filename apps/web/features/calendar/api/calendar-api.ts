import { getApiUrl } from '@/lib/api/base-url';
import { xsrfHeader } from '@/lib/api/client';

import type {
  CalendarConnectIntentRequest,
  CalendarConnectIntentResponse,
  CalendarConnection,
  CalendarToggleResponse,
  MailboxCalendarPreference,
  UpdateCalendarEnabledRequest,
  UpdateMailboxCalendarPreferenceRequest,
} from '@/features/calendar/types';

// TODO(12-W3): once `pnpm --filter web run generate:api` is unblocked (dev SSH
// tunnel + backend up — memory `reference_dev_db_ssh_tunnel.md`), switch the
// raw fetch wrappers below to the typed `api.GET/POST/PATCH/DELETE` against
// `paths['/api/calendar/...']` and replace `@/features/calendar/types` imports
// with `components['schemas']['CalendarConnectionResponse']` etc. The DTO
// records already carry `@Schema` annotations so the regenerated
// `lib/api/schema.d.ts` will publish the literal-union `status` / `role`
// fields the local types here mirror.
//
// The raw-fetch fallback follows the existing
// `features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api.ts`
// precedent — it manually echoes the XSRF cookie via `xsrfHeader()` and
// reuses `getApiUrl` so the dev base-url override works the same as for
// the typed client.

function jsonHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json', ...xsrfHeader() };
}

async function expectJson<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (!response.ok) {
    throw new Error(`${fallbackMessage}: ${response.status}`);
  }
  return (await response.json()) as T;
}

async function expectNoContent(response: Response, fallbackMessage: string): Promise<void> {
  if (!response.ok) {
    throw new Error(`${fallbackMessage}: ${response.status}`);
  }
}

export async function listCalendarConnections(mailboxId: string): Promise<CalendarConnection[]> {
  const response = await fetch(getApiUrl(`/api/calendar/mailboxes/${mailboxId}/connections`), {
    credentials: 'include',
  });
  return expectJson<CalendarConnection[]>(
    response,
    `/api/calendar/mailboxes/${mailboxId}/connections failed`,
  );
}

export async function disconnectCalendarConnection(calendarConnectionId: string): Promise<void> {
  const response = await fetch(getApiUrl(`/api/calendar/connections/${calendarConnectionId}`), {
    method: 'DELETE',
    credentials: 'include',
    headers: xsrfHeader(),
  });
  return expectNoContent(
    response,
    `/api/calendar/connections/${calendarConnectionId} DELETE failed`,
  );
}

export async function toggleCalendar(
  calendarId: string,
  enabled: boolean,
): Promise<CalendarToggleResponse> {
  const body: UpdateCalendarEnabledRequest = { enabled };
  const response = await fetch(getApiUrl(`/api/calendar/calendars/${calendarId}/enabled`), {
    method: 'PATCH',
    credentials: 'include',
    headers: jsonHeaders(),
    body: JSON.stringify(body),
  });
  return expectJson<CalendarToggleResponse>(
    response,
    `/api/calendar/calendars/${calendarId}/enabled PATCH failed`,
  );
}

export async function updateMailboxCalendarPreference(
  mailboxId: string,
  request: UpdateMailboxCalendarPreferenceRequest,
): Promise<MailboxCalendarPreference[]> {
  const response = await fetch(getApiUrl(`/api/calendar/mailboxes/${mailboxId}/preferences`), {
    method: 'PATCH',
    credentials: 'include',
    headers: jsonHeaders(),
    body: JSON.stringify(request),
  });
  return expectJson<MailboxCalendarPreference[]>(
    response,
    `/api/calendar/mailboxes/${mailboxId}/preferences PATCH failed`,
  );
}

export async function prepareCalendarConnect(
  mailboxId: string,
): Promise<CalendarConnectIntentResponse> {
  const body: CalendarConnectIntentRequest = { mailboxId };
  const response = await fetch(getApiUrl('/api/calendar/connect-intent'), {
    method: 'POST',
    credentials: 'include',
    headers: jsonHeaders(),
    body: JSON.stringify(body),
  });
  return expectJson<CalendarConnectIntentResponse>(
    response,
    '/api/calendar/connect-intent POST failed',
  );
}
