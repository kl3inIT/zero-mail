/**
 * Phase 12 W3 — TEMPORARY local type aliases for the Calendar feature.
 *
 * TODO(12-W3): Replace this file once the dev SSH tunnel + backend are reachable
 * so `pnpm --filter web run generate:api` can regenerate `lib/api/schema.d.ts`.
 * After regen, every usage of the types below should switch to:
 *   import type { components } from '@/lib/api/schema';
 *   export type CalendarConnection = components['schemas']['CalendarConnectionResponse'];
 *   export type CalendarSub = components['schemas']['CalendarSubResponse'];
 *   ...etc.
 *
 * The shapes here mirror the backend DTO records in
 * `backend/api/src/main/java/com/zeromail/api/dto/calendar/*.java` BYTE-FOR-BYTE.
 * Drift between this file and the records is a project bug — fix the backend, regen,
 * delete this file. We pay the temporary drift cost only because CLAUDE.md §11
 * forbids hand-editing `schema.d.ts` and the regen pipeline needs a running backend
 * (memory `reference_dev_db_ssh_tunnel.md`).
 */

export type CalendarConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'REVOKED';

export type CalendarRole = 'FREEBUSY' | 'EVENT_WRITE' | 'BRIEF_SOURCE';

export interface CalendarSub {
  id: string;
  externalCalendarId: string;
  name: string;
  isPrimary: boolean;
  isEnabled: boolean;
  timezone?: string | null;
}

export interface MailboxCalendarPreference {
  id: string;
  mailboxId: string;
  calendarConnectionId: string;
  calendarId: string;
  role: CalendarRole;
}

export interface CalendarConnection {
  id: string;
  googleEmail: string;
  status: CalendarConnectionStatus;
  connectedAt?: string | null;
  disconnectedAt?: string | null;
  googleProfileName?: string | null;
  googleProfilePictureUrl?: string | null;
  calendars: CalendarSub[];
  preferences: MailboxCalendarPreference[];
}

export interface UpdateCalendarEnabledRequest {
  enabled: boolean;
}

export interface UpdateMailboxCalendarPreferenceRequest {
  freebusyCalendarIds: string[];
  eventWriteCalendarId?: string | null;
  briefSourceCalendarId?: string | null;
}

export interface CalendarToggleResponse {
  preferencesRemoved: number;
}

export interface CalendarConnectIntentRequest {
  mailboxId: string;
}

export interface CalendarConnectIntentResponse {
  authorizationUrl: string;
}
