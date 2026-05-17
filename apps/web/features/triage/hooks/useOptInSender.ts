'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { optInSender } from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useOptInSender() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (senderEmail: string) => optInSender(senderEmail),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: triageKeys.senderSafetyNet() });
    },
  });
}
