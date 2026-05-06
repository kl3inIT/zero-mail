'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { accountQueryKeys } from '@/features/account/query-keys';
import { disconnectGmail } from '@/features/gmail/api/gmail-api';
import { gmailQueryKeys } from '@/features/gmail/query-keys';

export function useDisconnectGmail() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: disconnectGmail,
    onSuccess: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: gmailQueryKeys.all }),
        qc.invalidateQueries({ queryKey: accountQueryKeys.me() }),
      ]);
    },
  });
}
