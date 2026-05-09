'use client';

import { useQuery } from '@tanstack/react-query';

import { getCurrentUser } from '@/features/account/api/account-api';
import { accountQueryKeys } from '@/features/account/query-keys';

export function useCurrentUser() {
  return useQuery({
    queryKey: accountQueryKeys.me(),
    queryFn: ({ signal }) => getCurrentUser({ signal }),
  });
}
