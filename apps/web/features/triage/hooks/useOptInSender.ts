'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { optInSender, type ProtectedSendersResponse } from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useOptInSender() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (senderEmail: string) => optInSender(senderEmail),
    onSuccess: (response, senderEmail) => {
      queryClient.setQueryData<ProtectedSendersResponse>(
        triageKeys.senderSafetyNet(),
        (currentResponse) => ({
          senders: (currentResponse?.senders ?? []).map((sender) =>
            sender.senderEmail === (response.senderEmail ?? senderEmail)
              ? { ...sender, optedIn: response.optedIn ?? true }
              : sender,
          ),
        }),
      );
    },
  });
}
