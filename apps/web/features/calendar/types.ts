/**
 * Phase 12 G1 — Calendar feature wire types, generated from the backend OpenAPI doc.
 *
 * The W3 hand-mirrored shim was replaced once `pnpm --filter web run generate:api` could run
 * (the dev SSH tunnel came up and the `:backend:api:generateOpenApiDocs` emit boot was fixed). The
 * named aliases below preserve the public type names every calendar component/hook already imports
 * while sourcing their definitions from `components['schemas'][...]` — so a future backend DTO
 * change auto-propagates on the next regen instead of drifting against a hand-edited copy.
 */
import type { components } from '@/lib/api/schema';

export type CalendarConnection = components['schemas']['CalendarConnectionResponse'];
export type CalendarSub = components['schemas']['CalendarSubResponse'];
export type MailboxCalendarPreference = components['schemas']['MailboxCalendarPreferenceResponse'];
export type UpdateCalendarEnabledRequest = components['schemas']['UpdateCalendarEnabledRequest'];
export type UpdateMailboxCalendarPreferenceRequest =
  components['schemas']['UpdateMailboxCalendarPreferenceRequest'];
export type CalendarToggleResponse = components['schemas']['CalendarToggleResponse'];
export type CalendarConnectIntentRequest = components['schemas']['CalendarConnectIntentRequest'];
export type CalendarConnectIntentResponse = components['schemas']['CalendarConnectIntentResponse'];

export type CalendarConnectionStatus = CalendarConnection['status'];
export type CalendarRole = MailboxCalendarPreference['role'];
