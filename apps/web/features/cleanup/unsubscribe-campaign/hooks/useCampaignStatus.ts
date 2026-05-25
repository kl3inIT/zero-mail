'use client';

import { useQuery } from '@tanstack/react-query';

import { fetchCampaignStatus } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

export function useCampaignStatus(jobId: string) {
  return useQuery({
    queryKey: unsubscribeCampaignKeys.byId(jobId),
    queryFn: () => fetchCampaignStatus(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'QUEUED' || status === 'RUNNING' ? 2000 : false;
    },
    refetchOnWindowFocus: false,
  });
}
