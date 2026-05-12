'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getShadowMode, setShadowMode } from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useShadowModeState() {
  return useQuery({ queryKey: triageKeys.shadowMode(), queryFn: getShadowMode });
}

export function useSetShadowMode() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (enabled: boolean) => setShadowMode(enabled),
    onSuccess: async (state) => {
      queryClient.setQueryData(triageKeys.shadowMode(), state);
      await queryClient.invalidateQueries({ queryKey: triageKeys.shadowMode() });
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
    readUnavailable: state.data?.readUnavailable ?? false,
  };
}
