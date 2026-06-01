'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { deleteByok } from '@/features/ai/api/byok-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

export function useDeleteByok() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<void, Error, void>({
    mutationFn: deleteByok,
    meta: {
      successMessage: t('ai.toast.byokDeleted'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onSuccess: async () => {
      queryClient.setQueryData(aiSettingsKeys.byok(), null);
      await queryClient.invalidateQueries({ queryKey: aiSettingsKeys.byok() });
    },
  });
}
