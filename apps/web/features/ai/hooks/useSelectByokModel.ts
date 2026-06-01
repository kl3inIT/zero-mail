'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { selectByokModel, type ByokResponse } from '@/features/ai/api/byok-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

export function useSelectByokModel() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<ByokResponse, Error, string>({
    mutationFn: selectByokModel,
    meta: {
      successMessage: t('ai.toast.aiPreferenceSaved'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onSuccess: async (savedByok) => {
      queryClient.setQueryData(aiSettingsKeys.byok(), savedByok);
      await queryClient.invalidateQueries({ queryKey: aiSettingsKeys.byok() });
    },
  });
}
