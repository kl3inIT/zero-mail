'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { disconnectGmail } from '@/features/gmail/api/disconnect';
import { gmailKeys } from '@/features/gmail/api/keys';

export function useDisconnectGmail() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: disconnectGmail,
    onSuccess: () => qc.invalidateQueries({ queryKey: gmailKeys.all }),
  });
}
