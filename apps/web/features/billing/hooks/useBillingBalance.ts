'use client';

import { useQuery } from '@tanstack/react-query';

import { getBillingBalance } from '@/features/billing/api/billing-api';
import { billingKeys } from '@/features/billing/query-keys';

export const BILLING_BALANCE_REFETCH_INTERVAL_MS = 45_000;
export const BILLING_BALANCE_STALE_TIME_MS = 30_000;

export function useBillingBalance() {
  return useQuery({
    queryKey: billingKeys.balance(),
    queryFn: ({ signal }) => getBillingBalance({ signal }),
    refetchInterval: BILLING_BALANCE_REFETCH_INTERVAL_MS,
    refetchIntervalInBackground: false,
    staleTime: BILLING_BALANCE_STALE_TIME_MS,
  });
}
