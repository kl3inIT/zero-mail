'use client';

import { useQuery } from '@tanstack/react-query';

import { getCurrentUser } from '@/features/account/api/account-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useTriagePauseState() {
  return useQuery({
    queryKey: triageKeys.pauseState(),
    queryFn: async ({ signal }) => {
      const user = await getCurrentUser({ signal });
      return user.triagePaused;
    },
  });
}
