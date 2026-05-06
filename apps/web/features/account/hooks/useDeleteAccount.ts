'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { deleteAccount } from '@/features/account/api/account-api';
import { accountQueryKeys } from '@/features/account/query-keys';

export function useDeleteAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteAccount,
    onSuccess: () => qc.invalidateQueries({ queryKey: accountQueryKeys.all }),
  });
}
