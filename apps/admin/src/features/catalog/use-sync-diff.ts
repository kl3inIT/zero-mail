import { useQuery } from '@tanstack/react-query';

import { fetchCatalogSyncDiff } from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export function useSyncDiff(jobId: string) {
  return useQuery({
    queryKey: catalogQueryKeys.syncJob(jobId),
    queryFn: () => fetchCatalogSyncDiff(jobId),
    refetchInterval: (query) => (query.state.data?.status === 'AWAITING_CONFIRM' ? false : 2000),
  });
}
