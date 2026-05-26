'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { deleteProtectedSender } from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useDeleteProtectedSender() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<void, Error, string>({
    mutationFn: deleteProtectedSender,
    meta: {
      successMessage: t('ai.toast.safetyNetRemoved'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: triageKeys.senderSafetyNet() });
    },
  });
}
