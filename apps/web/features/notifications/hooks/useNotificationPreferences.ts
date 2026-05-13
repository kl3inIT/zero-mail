'use client';

import { useQuery } from '@tanstack/react-query';

import { fetchNotificationPreferences } from '@/features/notifications/api/notifications-api';
import { notificationsKeys } from '@/features/notifications/query-keys';

export function useNotificationPreferences() {
  return useQuery({
    queryKey: notificationsKeys.preferences(),
    queryFn: fetchNotificationPreferences,
    staleTime: 5 * 60_000,
  });
}
