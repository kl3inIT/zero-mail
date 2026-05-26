'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import {
  updateVoiceSettings,
  type VoiceSettings,
  type VoiceSettingsUpdate,
} from '@/features/ai/api/ai-settings-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

export function useUpdateVoiceSettings() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<VoiceSettings, Error, VoiceSettingsUpdate>({
    mutationFn: updateVoiceSettings,
    meta: {
      successMessage: t('ai.toast.voiceSaved'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onSuccess: (savedSettings) => {
      queryClient.setQueryData(aiSettingsKeys.voice(), savedSettings);
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: aiSettingsKeys.voice() });
    },
  });
}
