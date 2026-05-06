'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { accountQueryKeys } from '@/features/account/query-keys';
import { selectTemplate, type SelectTemplateBody } from '@/features/onboarding/api/onboarding-api';

export function useSelectTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SelectTemplateBody) => selectTemplate(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountQueryKeys.me() }),
  });
}
