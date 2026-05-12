'use client';

import { useMutation } from '@tanstack/react-query';

import { createTopupIntent } from '@/features/billing/api/billing-api';

export function useCreateTopupIntent() {
  return useMutation({
    mutationFn: (amountVnd: number) => createTopupIntent(amountVnd),
  });
}
