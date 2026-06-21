'use client';

import { CalendarConnectionsPanel } from '@/features/calendar/components/CalendarConnectionsPanel';

interface CalendarSettingsClientProps {
  mailboxId: string;
}

/**
 * Client-side wrapper for the calendar settings page. Holds the TanStack Query
 * boundary; the parent route stays an RSC so it can read params + render the
 * server-side title/subtitle pair.
 */
export function CalendarSettingsClient({ mailboxId }: CalendarSettingsClientProps) {
  return <CalendarConnectionsPanel mailboxId={mailboxId} />;
}
