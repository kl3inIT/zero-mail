'use client';

import { useMutation } from '@tanstack/react-query';

import {
  createBillingCheckout,
  type BillingCheckoutRequest,
} from '@/features/billing/api/billing-api';

export function useStartBillingCheckout() {
  return useMutation({
    mutationFn: (request: BillingCheckoutRequest) => createBillingCheckout(request),
  });
}
