'use client';

import { useQuery } from '@tanstack/react-query';

import { getCurrentUser } from '@/features/account/api/me';
import { accountKeys } from '@/features/account/api/keys';

export function useCurrentUser() {
  return useQuery({
    queryKey: accountKeys.me(),
    queryFn: ({ signal }) => getCurrentUser({ signal }),
  });
}
