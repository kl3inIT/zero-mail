'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { disconnectCalendarConnection } from '@/features/calendar/api/calendar-api';
import { calendarQueryKeys } from '@/features/calendar/query-keys';

/**
 * Mutation hook to disconnect a calendar connection.
 *
 * Per CLAUDE.md §12 — uses meta.successMessage / meta.errorMessage so the
 * global MutationCache handler in lib/query-client.tsx surfaces the toast.
 * No local toast.success/error call.
 */
export function useDisconnectCalendarConnection(mailboxId: string) {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation({
    mutationFn: (calendarConnectionId: string) =>
      disconnectCalendarConnection(calendarConnectionId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: calendarQueryKeys.connections(mailboxId) }),
    meta: {
      successMessage: t('calendar.actions.disconnect.success'),
      errorMessage: t('calendar.actions.disconnect.error'),
    },
  });
}
