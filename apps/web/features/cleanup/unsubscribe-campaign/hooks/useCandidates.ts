'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { fetchCandidates } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import type { DateRangeSpec } from '@/features/cleanup/unsubscribe-campaign/date-range-spec';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

export function useCandidates(spec: DateRangeSpec, limit: number = 25) {
  return useQuery({
    queryKey: unsubscribeCampaignKeys.candidates(spec, limit),
    queryFn: () => fetchCandidates(spec, limit),
    // Inbox Zero parity: keep the last result on screen while the new one loads — no skeleton
    // flash when changing window/limit or after invalidation. 5-minute staleTime so opening the
    // page twice within a session does not re-hit Gmail/DB.
    staleTime: 5 * 60_000,
    gcTime: 30 * 60_000,
    placeholderData: keepPreviousData,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
  });
}
