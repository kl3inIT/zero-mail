'use client';

import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { generateVoiceFromSent } from '@/features/ai/api/ai-settings-api';

export function useGenerateVoiceFromSent() {
  const t = useTranslations();

  return useMutation({
    mutationFn: () => generateVoiceFromSent(20),
    meta: {
      successMessage: t('ai.toast.voiceGenerated'),
      errorMessage: t('errors.voice.generate.failed'),
    },
  });
}
