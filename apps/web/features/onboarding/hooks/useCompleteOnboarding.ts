'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { accountQueryKeys } from '@/features/account/query-keys';
import { completeOnboarding } from '@/features/onboarding/api/onboarding-api';

export function useCompleteOnboarding() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: completeOnboarding,
    onSuccess: () => qc.invalidateQueries({ queryKey: accountQueryKeys.me() }),
  });
}
