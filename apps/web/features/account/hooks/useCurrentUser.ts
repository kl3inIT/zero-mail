'use client';

import { useQuery } from '@tanstack/react-query';

import { getCurrentUser, type CurrentUser } from '@/features/account/api/account-api';
import { accountQueryKeys } from '@/features/account/query-keys';

// `initialData` lets RSC pages hand server-fetched data to the client so the
// first paint already has the value (no isPending flicker). The query still
// re-fetches in the background on staleTime expiry.
export function useCurrentUser(initialData?: CurrentUser) {
  return useQuery({
    queryKey: accountQueryKeys.me(),
    queryFn: ({ signal }) => getCurrentUser({ signal }),
    initialData,
  });
}
