'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { accountKeys } from '@/features/account/api/keys';
import { setTriagePaused } from '@/features/triage/api/triagePause';

export function useToggleTriagePause() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (paused: boolean) => setTriagePaused(paused),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.me() }),
  });
}
