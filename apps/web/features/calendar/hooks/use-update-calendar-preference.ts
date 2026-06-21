'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { updateMailboxCalendarPreference } from '@/features/calendar/api/calendar-api';
import { calendarQueryKeys } from '@/features/calendar/query-keys';
import type { UpdateMailboxCalendarPreferenceRequest } from '@/features/calendar/types';

/**
 * Mutation hook for the per-role assignment "Save" button. PATCHes the full
 * replacement payload (FREEBUSY multi-select + EVENT_WRITE single + BRIEF_SOURCE
 * single) and invalidates the connection list so the role-assignment section
 * re-renders with the newly-persisted rows.
 */
export function useUpdateCalendarPreference(mailboxId: string) {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation({
    mutationFn: (request: UpdateMailboxCalendarPreferenceRequest) =>
      updateMailboxCalendarPreference(mailboxId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: calendarQueryKeys.connections(mailboxId) }),
    meta: {
      successMessage: t('calendar.actions.preference.success'),
      errorMessage: t('calendar.actions.preference.error'),
    },
  });
}
