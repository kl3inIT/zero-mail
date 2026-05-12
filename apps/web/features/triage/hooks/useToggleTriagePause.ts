'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { accountQueryKeys } from '@/features/account/query-keys';
import { billingKeys } from '@/features/billing/query-keys';
import { setTriagePaused } from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useToggleTriagePause() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (paused: boolean) => setTriagePaused(paused),
    onMutate: async (paused) => {
      await queryClient.cancelQueries({ queryKey: triageKeys.pauseState() });
      const previousPauseState = queryClient.getQueryData<boolean>(triageKeys.pauseState());

      queryClient.setQueryData<boolean>(triageKeys.pauseState(), paused);

      return { previousPauseState };
    },
    onError: (_mutationError, _paused, context) => {
      if (context?.previousPauseState !== undefined) {
        queryClient.setQueryData(triageKeys.pauseState(), context.previousPauseState);
      }
    },
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: triageKeys.pauseState() }),
        queryClient.invalidateQueries({ queryKey: billingKeys.balance() }),
        queryClient.invalidateQueries({ queryKey: accountQueryKeys.me() }),
      ]);
    },
  });
}
