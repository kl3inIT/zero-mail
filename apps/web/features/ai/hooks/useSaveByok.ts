'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { saveByok, type ByokResponse, type ByokSaveRequest } from '@/features/ai/api/byok-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

export function useSaveByok() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<ByokResponse, Error, ByokSaveRequest>({
    mutationFn: saveByok,
    meta: {
      successMessage: t('ai.toast.byokKeySaved'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onSuccess: async (savedByok) => {
      queryClient.setQueryData(aiSettingsKeys.byok(), savedByok);
      await queryClient.invalidateQueries({ queryKey: aiSettingsKeys.byok() });
    },
  });
}
