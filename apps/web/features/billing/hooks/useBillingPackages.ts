'use client';

import { useQuery } from '@tanstack/react-query';

import { listBillingPackages } from '@/features/billing/api/billing-api';
import { billingKeys } from '@/features/billing/query-keys';

export function useBillingPackages() {
  return useQuery({
    queryKey: billingKeys.packages(),
    queryFn: listBillingPackages,
    staleTime: 5 * 60 * 1000,
  });
}
