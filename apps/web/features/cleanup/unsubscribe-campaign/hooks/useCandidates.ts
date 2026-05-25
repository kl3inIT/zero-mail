'use client';

import { useQuery } from '@tanstack/react-query';

import { fetchCandidates } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

export function useCandidates(window: string = '30d', limit: number = 25) {
  return useQuery({
    queryKey: unsubscribeCampaignKeys.candidates(window),
    queryFn: () => fetchCandidates(window, limit),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}
