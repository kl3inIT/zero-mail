'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import {
  updateNotificationPreferences,
  type NotificationPreferencesResponse,
  type NotificationPreferencesUpdateRequest,
} from '@/features/notifications/api/notifications-api';
import { notificationsKeys } from '@/features/notifications/query-keys';

type MutationContext = {
  previousPreferences: NotificationPreferencesResponse | undefined;
};

export function useUpdateNotificationPreferences() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<
    NotificationPreferencesResponse,
    Error,
    NotificationPreferencesUpdateRequest,
    MutationContext
  >({
    mutationFn: updateNotificationPreferences,
    onMutate: async (nextPreferences) => {
      await queryClient.cancelQueries({ queryKey: notificationsKeys.preferences() });
      const previousPreferences = queryClient.getQueryData<NotificationPreferencesResponse>(
        notificationsKeys.preferences(),
      );

      queryClient.setQueryData<NotificationPreferencesResponse>(
        notificationsKeys.preferences(),
        (currentPreferences) => ({
          channel: currentPreferences?.channel ?? 'DAILY_DIGEST',
          timeZone: currentPreferences?.timeZone ?? 'Asia/Ho_Chi_Minh',
          digestEnabled: nextPreferences.digestEnabled,
          digestSendHourLocal:
            nextPreferences.digestSendHourLocal ??
            currentPreferences?.digestSendHourLocal ??
            previousPreferences?.digestSendHourLocal ??
            20,
        }),
      );

      return { previousPreferences };
    },
    onSuccess: (savedPreferences) => {
      queryClient.setQueryData(notificationsKeys.preferences(), savedPreferences);
      toast.success(t('settings.notifications.toast.savedTitle'));
    },
    onError: (_mutationError, variables, context) => {
      queryClient.setQueryData(notificationsKeys.preferences(), context?.previousPreferences);
      toast.error(t('settings.notifications.toast.errorTitle'), {
        action: {
          label: t('settings.notifications.toast.retry'),
          onClick: () => {
            void updateNotificationPreferences(variables)
              .then((savedPreferences) => {
                queryClient.setQueryData(notificationsKeys.preferences(), savedPreferences);
                toast.success(t('settings.notifications.toast.savedTitle'));
              })
              .catch(() => {
                toast.error(t('settings.notifications.toast.errorTitle'));
              })
              .finally(() => {
                void queryClient.invalidateQueries({ queryKey: notificationsKeys.preferences() });
              });
          },
        },
      });
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: notificationsKeys.preferences() });
    },
  });
}
