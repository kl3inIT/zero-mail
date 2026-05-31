'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { testByokConnection, type ByokTestConnectionResponse } from '@/features/ai/api/byok-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

export function useTestByokConnection() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<ByokTestConnectionResponse, Error, void>({
    mutationFn: testByokConnection,
    meta: {
      errorMessage: t('errors.ai.byok.no_row'),
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: aiSettingsKeys.byok() });
    },
  });
}
