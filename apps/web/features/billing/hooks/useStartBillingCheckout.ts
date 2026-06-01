'use client';

import { useMutation } from '@tanstack/react-query';

import { createBillingCheckout } from '@/features/billing/api/billing-api';

export function useStartBillingCheckout() {
  return useMutation({ mutationFn: createBillingCheckout });
}
