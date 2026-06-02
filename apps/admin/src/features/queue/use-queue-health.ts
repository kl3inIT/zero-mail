import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { fetchQueueHealth } from './queue-api';
import { queueQueryKeys } from './query-keys';

export const QUEUE_REFRESH_INTERVAL_MS = 5_000;

/**
 * 5s auto-refreshing query for the queue health snapshot — tight enough to surface a stuck job
 * quickly. Pauses when the document is hidden (per UI-SPEC §Interaction Pattern 2) and when the
 * caller passes `paused=true`.
 */
export function useQueueHealth(options: { paused: boolean }) {
  const documentHidden = useDocumentHidden();
  const refetchInterval =
    options.paused || documentHidden ? false : QUEUE_REFRESH_INTERVAL_MS;
  return useQuery({
    queryKey: queueQueryKeys.health(),
    queryFn: fetchQueueHealth,
    refetchInterval,
    refetchIntervalInBackground: false,
  });
}

function useDocumentHidden(): boolean {
  const [hidden, setHidden] = useState(
    typeof document === 'undefined' ? false : document.hidden,
  );
  useEffect(() => {
    if (typeof document === 'undefined') return;
    const handler = () => setHidden(document.hidden);
    document.addEventListener('visibilitychange', handler);
    return () => document.removeEventListener('visibilitychange', handler);
  }, []);
  return hidden;
}
