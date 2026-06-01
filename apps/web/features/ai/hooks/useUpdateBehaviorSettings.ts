'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import {
  updateBehaviorSettings,
  type BehaviorSettings,
  type BehaviorSettingsUpdate,
} from '@/features/ai/api/ai-settings-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

type MutationContext = {
  previousSettings?: BehaviorSettings;
};

const DEFAULT_BEHAVIOR_SETTINGS: BehaviorSettings = {
  autoDraftReplies: false,
  draftConfidence: 'MEDIUM',
  sensitiveDataProtection: true,
};

export function useUpdateBehaviorSettings() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<BehaviorSettings, Error, BehaviorSettingsUpdate, MutationContext>({
    mutationFn: updateBehaviorSettings,
    meta: {
      successMessage: t('ai.toast.behaviorSaved'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onMutate: async (nextSettings) => {
      await queryClient.cancelQueries({ queryKey: aiSettingsKeys.behavior() });
      const previousSettings = queryClient.getQueryData<BehaviorSettings>(
        aiSettingsKeys.behavior(),
      );
      queryClient.setQueryData<BehaviorSettings>(aiSettingsKeys.behavior(), {
        ...(previousSettings ?? DEFAULT_BEHAVIOR_SETTINGS),
        ...nextSettings,
      });
      return { previousSettings };
    },
    onError: (_mutationError, _nextSettings, context) => {
      if (context?.previousSettings) {
        queryClient.setQueryData(aiSettingsKeys.behavior(), context.previousSettings);
      }
    },
    onSuccess: (savedSettings) => {
      queryClient.setQueryData(aiSettingsKeys.behavior(), savedSettings);
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: aiSettingsKeys.behavior() });
    },
  });
}
