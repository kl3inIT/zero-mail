'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { deleteAccount } from '@/features/account/api/deleteAccount';
import { accountKeys } from '@/features/account/api/keys';

export function useDeleteAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteAccount,
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.all }),
  });
}
