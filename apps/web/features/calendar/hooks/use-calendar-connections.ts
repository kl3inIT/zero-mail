'use client';

import { useQuery } from '@tanstack/react-query';

import { listCalendarConnections } from '@/features/calendar/api/calendar-api';
import { calendarQueryKeys } from '@/features/calendar/query-keys';

/**
 * TanStack Query hook for the per-mailbox calendar connection list. Short
 * {@code staleTime} (5s) so the post-OAuth refresh sees the new row promptly
 * without forcing a hard refetch in the OAuth success redirect handler.
 */
export function useCalendarConnections(mailboxId: string) {
  return useQuery({
    queryKey: calendarQueryKeys.connections(mailboxId),
    queryFn: () => listCalendarConnections(mailboxId),
    staleTime: 5_000,
    enabled: Boolean(mailboxId),
  });
}
