'use client';

import { useQuery } from '@tanstack/react-query';

import { getBillingBalance } from '@/features/billing/api/billing-api';
import {
  BILLING_BALANCE_REFETCH_INTERVAL_MS,
  BILLING_BALANCE_STALE_TIME_MS,
} from '@/features/billing/hooks/useBillingBalance';
import { billingKeys } from '@/features/billing/query-keys';

type UseTopupCreditWatchOptions = {
  baselineCredits: number;
  expiresAt?: string | null;
  enabled?: boolean;
};

function isExpired(expiresAt?: string | null): boolean {
  if (!expiresAt) return false;
  const expiryTime = Date.parse(expiresAt);
  return Number.isFinite(expiryTime) && expiryTime <= Date.now();
}

function isCredited(availableCredits: number | undefined, baselineCredits: number): boolean {
  return typeof availableCredits === 'number' && availableCredits > baselineCredits;
}

export function useTopupCreditWatch({
  baselineCredits,
  expiresAt,
  enabled = true,
}: UseTopupCreditWatchOptions) {
  const query = useQuery({
    queryKey: billingKeys.topupBalanceWatch(),
    queryFn: ({ signal }) => getBillingBalance({ signal }),
    enabled,
    // GAP: no intent-status endpoint as of 05A, so credited is inferred from balance rising.
    refetchInterval: (queryState) => {
      const availableCredits = queryState.state.data?.availableCredits;
      if (isExpired(expiresAt) || isCredited(availableCredits, baselineCredits)) return false;
      return BILLING_BALANCE_REFETCH_INTERVAL_MS;
    },
    refetchIntervalInBackground: false,
    staleTime: BILLING_BALANCE_STALE_TIME_MS,
  });

  const availableCredits = query.data?.availableCredits;

  return {
    ...query,
    balance: query.data,
    credited: isCredited(availableCredits, baselineCredits),
    expired: isExpired(expiresAt),
  };
}
