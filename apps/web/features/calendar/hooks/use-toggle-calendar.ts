'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { toggleCalendar } from '@/features/calendar/api/calendar-api';
import { calendarQueryKeys } from '@/features/calendar/query-keys';

interface ToggleCalendarInput {
  calendarId: string;
  enabled: boolean;
}

/**
 * Mutation hook to flip a sub-calendar's {@code is_enabled} flag. Cascade-deletes
 * dependent preference rows server-side (D-13); the backend response carries
 * {@code preferencesRemoved} which the UI consumes to refresh the role-assignment
 * section after invalidation.
 */
export function useToggleCalendar(mailboxId: string) {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation({
    mutationFn: ({ calendarId, enabled }: ToggleCalendarInput) =>
      toggleCalendar(calendarId, enabled),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: calendarQueryKeys.connections(mailboxId) }),
    meta: {
      successMessage: t('calendar.actions.toggle.success'),
      errorMessage: t('calendar.actions.toggle.error'),
    },
  });
}
