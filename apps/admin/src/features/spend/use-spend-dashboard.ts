import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { spendQueryKeys } from './query-keys';
import { fetchSpendDashboard, type SpendQueryInput } from './spend-api';

/**
 * 60s auto-refresh interval (slower than /queue's 10s per UI-SPEC §Interaction Pattern 2 —
 * spend data does not change as rapidly as worker queue depth).
 */
export const SPEND_REFRESH_INTERVAL_MS = 60_000;

export function useSpendDashboard(input: SpendQueryInput, options: { paused: boolean }) {
  const documentHidden = useDocumentHidden();
  const refetchInterval =
    options.paused || documentHidden ? false : SPEND_REFRESH_INTERVAL_MS;
  return useQuery({
    queryKey: spendQueryKeys.dashboard(input),
    queryFn: () => fetchSpendDashboard(input),
    refetchInterval,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: false,
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
