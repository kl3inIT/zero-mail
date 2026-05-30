'use client';

import { useInfiniteQuery } from '@tanstack/react-query';

import { getLedgerHistory } from '@/features/billing/api/billing-api';
import { billingKeys } from '@/features/billing/query-keys';

export const BILLING_LEDGER_PAGE_SIZE = 10;

export function useLedgerHistory(pageSize = BILLING_LEDGER_PAGE_SIZE) {
  return useInfiniteQuery({
    queryKey: billingKeys.ledger(pageSize),
    queryFn: ({ pageParam, signal }) =>
      getLedgerHistory({ limit: pageSize, cursor: pageParam, signal }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });
}
