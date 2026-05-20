import { useQuery } from '@tanstack/react-query';

import { fetchCatalogSyncDiff } from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export function useSyncDiff(jobId: string) {
  return useQuery({
    queryKey: catalogQueryKeys.syncJob(jobId),
    queryFn: () => fetchCatalogSyncDiff(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      // Only keep polling while the worker is still producing the diff. All other
      // states (AWAITING_CONFIRM, CONFIRMED, CANCELLED, FAILED) are terminal for
      // polling purposes — the user must act or the job is finished.
      return status === 'IN_PROGRESS' ? 2000 : false;
    },
  });
}
