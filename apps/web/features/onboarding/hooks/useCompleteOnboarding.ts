'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { completeOnboarding } from '@/features/onboarding/api/complete';
import { onboardingKeys } from '@/features/onboarding/api/keys';
import { accountKeys } from '@/features/account/api/keys';

export function useCompleteOnboarding() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: completeOnboarding,
    onSuccess: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: onboardingKeys.all }),
        qc.invalidateQueries({ queryKey: accountKeys.me() }),
      ]);
    },
  });
}
