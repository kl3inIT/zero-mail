'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { accountQueryKeys } from '@/features/account/query-keys';
import { setTriagePaused } from '@/features/triage/api/triage-api';

export function useToggleTriagePause() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (paused: boolean) => setTriagePaused(paused),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountQueryKeys.me() }),
  });
}
