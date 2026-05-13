'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  getShadowMode,
  setShadowMode,
  type ShadowModeState,
} from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useShadowModeState() {
  return useQuery({ queryKey: triageKeys.shadowMode(), queryFn: getShadowMode });
}

export function useSetShadowMode() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (enabled: boolean) => setShadowMode(enabled),
    onSuccess: (state) => {
      queryClient.setQueryData<ShadowModeState>(triageKeys.shadowMode(), state);
    },
  });
}

export function useShadowMode() {
  const state = useShadowModeState();
  const mutation = useSetShadowMode();

  return {
    state,
    mutation,
    enabled: state.data?.enabled ?? false,
  };
}
