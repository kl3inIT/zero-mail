import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { fetchAdminOverview, type AdminOverviewQueryInput } from './overview-api';
import { overviewQueryKeys } from './query-keys';

export const OVERVIEW_REFRESH_INTERVAL_MS = 60_000;

export function useAdminOverview(
  input: AdminOverviewQueryInput,
  options: { paused: boolean },
) {
  const documentHidden = useDocumentHidden();
  const refetchInterval =
    options.paused || documentHidden ? false : OVERVIEW_REFRESH_INTERVAL_MS;
  return useQuery({
    queryKey: overviewQueryKeys.dashboard(input),
    queryFn: () => fetchAdminOverview(input),
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
