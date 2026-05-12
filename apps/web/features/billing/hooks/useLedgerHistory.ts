'use client';

import { useInfiniteQuery } from '@tanstack/react-query';

import { getLedgerHistory } from '@/features/billing/api/billing-api';
import { billingKeys } from '@/features/billing/query-keys';

/**
 * GAP: no backend ledger-history list endpoint as of 05A — see 05A-RESEARCH.md A4.
 * Screens must render the first page's `unavailable` flag as "not yet available",
 * distinct from an available-but-empty ledger.
 */
export function useLedgerHistory() {
  return useInfiniteQuery({
    queryKey: billingKeys.ledger(),
    queryFn: () => getLedgerHistory(),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) =>
      lastPage.unavailable ? undefined : (lastPage.nextCursor ?? undefined),
  });
}
