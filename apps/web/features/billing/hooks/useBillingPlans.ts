'use client';

import { useQuery } from '@tanstack/react-query';

import { getBillingPlans } from '@/features/billing/api/billing-api';
import { billingKeys } from '@/features/billing/query-keys';

export const BILLING_PLANS_STALE_TIME_MS = 5 * 60_000;

export function useBillingPlans() {
  return useQuery({
    queryKey: billingKeys.plans(),
    queryFn: () => getBillingPlans(),
    staleTime: BILLING_PLANS_STALE_TIME_MS,
  });
}
