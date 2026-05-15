'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import type { CurrentUser } from '@/features/account/api/account-api';
import { accountQueryKeys } from '@/features/account/query-keys';
import { billingKeys } from '@/features/billing/query-keys';
import { setTriagePaused } from '@/features/triage/api/triage-api';

export function useToggleTriagePause() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (paused: boolean) => setTriagePaused(paused),
    onMutate: async (paused) => {
      await queryClient.cancelQueries({ queryKey: accountQueryKeys.me() });
      const previousUser = queryClient.getQueryData<CurrentUser>(accountQueryKeys.me());

      if (previousUser) {
        queryClient.setQueryData<CurrentUser>(accountQueryKeys.me(), {
          ...previousUser,
          triagePaused: paused,
        });
      }

      return { previousUser };
    },
    onError: (_mutationError, _paused, context) => {
      if (context?.previousUser) {
        queryClient.setQueryData(accountQueryKeys.me(), context.previousUser);
      }
    },
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: accountQueryKeys.me() }),
        queryClient.invalidateQueries({ queryKey: billingKeys.balance() }),
      ]);
    },
  });
}
