'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { updateLanguage } from '@/features/account/api/account-api';
import { accountQueryKeys } from '@/features/account/query-keys';

export function useUpdateLanguage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: updateLanguage,
    onSuccess: () => qc.invalidateQueries({ queryKey: accountQueryKeys.me() }),
  });
}
