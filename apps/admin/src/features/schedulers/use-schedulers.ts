import { useQuery } from '@tanstack/react-query';

import { fetchSchedulers } from './schedulers-api';
import { schedulerQueryKeys } from './query-keys';

/** Read-only catalog of every background scheduler (cron + fixed-delay) across API + WORKER. */
export function useSchedulers() {
  return useQuery({
    queryKey: schedulerQueryKeys.list(),
    queryFn: fetchSchedulers,
  });
}
