'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { deleteAccount } from '@/features/account/api/account-api';
import { accountQueryKeys } from '@/features/account/query-keys';

export function useDeleteAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteAccount,
    // The account no longer exists once this resolves, so we must NOT invalidate
    // (which would refetch `/me`, get a 401, and surface a spurious
    // background-refetch error toast right as the caller redirects to /login).
    // Drop the cached account queries instead; the caller owns the redirect.
    onSuccess: () => qc.removeQueries({ queryKey: accountQueryKeys.all }),
  });
}
