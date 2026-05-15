'use client';

import { useQuery } from '@tanstack/react-query';

import { getCurrentUser } from '@/features/account/api/account-api';
import { accountQueryKeys } from '@/features/account/query-keys';

// Reads `triagePaused` off the shared /me cache entry so the pause flag stays in
// sync with useCurrentUser instead of living in a parallel triage cache.
export function useTriagePauseState() {
  return useQuery({
    queryKey: accountQueryKeys.me(),
    queryFn: ({ signal }) => getCurrentUser({ signal }),
    select: (user) => user.triagePaused,
  });
}
