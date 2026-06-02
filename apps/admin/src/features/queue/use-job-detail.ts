import { useQuery } from '@tanstack/react-query';

import { fetchJobDetail } from './queue-api';
import { queueQueryKeys } from './query-keys';

/** Loads metadata-only detail for one job. Enabled only when a job id is selected. */
export function useJobDetail(jobId: string | null) {
  return useQuery({
    queryKey: queueQueryKeys.jobDetail(jobId ?? ''),
    queryFn: () => fetchJobDetail(jobId as string),
    enabled: Boolean(jobId),
  });
}
