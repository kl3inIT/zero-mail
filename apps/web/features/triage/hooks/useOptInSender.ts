'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { optInSender } from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useOptInSender() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation({
    mutationFn: (senderEmail: string) => optInSender(senderEmail),
    meta: {
      successMessage: t('ai.toast.safetyNetAdded'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: triageKeys.senderSafetyNet() });
    },
  });
}
