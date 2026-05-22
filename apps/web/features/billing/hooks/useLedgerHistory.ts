'use client';

import { useInfiniteQuery } from '@tanstack/react-query';

import { getLedgerHistory } from '@/features/billing/api/billing-api';
import { billingKeys } from '@/features/billing/query-keys';

export function useLedgerHistory() {
  return useInfiniteQuery({
    queryKey: billingKeys.ledger(),
    queryFn: () => getLedgerHistory(),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });
}
