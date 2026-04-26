'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { selectTemplate, type SelectTemplateBody } from '@/features/onboarding/api/selectTemplate';
import { onboardingKeys } from '@/features/onboarding/api/keys';
import { accountKeys } from '@/features/account/api/keys';

export function useSelectTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SelectTemplateBody) => selectTemplate(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: onboardingKeys.all });
      qc.invalidateQueries({ queryKey: accountKeys.me() });
    },
  });
}
