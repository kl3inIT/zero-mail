import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { fetchJobs, type JobsFilter } from './queue-api';
import { QUEUE_REFRESH_INTERVAL_MS } from './use-queue-health';
import { queueQueryKeys } from './query-keys';

/**
 * Unified job list across every processing_job type. Polls on the shared queue interval (paused
 * when the operator pauses auto-refresh or the tab is backgrounded) so a stuck job surfaces
 * without a manual refresh. Keeps previous page data while a new filter/page loads to avoid a
 * flash of empty table.
 */
export function useJobs(
  filter: JobsFilter,
  cursor: string | null,
  limit = 25,
  options: { paused: boolean },
) {
  return useQuery({
    queryKey: queueQueryKeys.jobs(filter.status ?? null, filter.jobType ?? null, cursor, limit),
    queryFn: () => fetchJobs(filter, cursor, limit),
    refetchInterval: options.paused ? false : QUEUE_REFRESH_INTERVAL_MS,
    refetchIntervalInBackground: false,
    placeholderData: keepPreviousData,
  });
}
