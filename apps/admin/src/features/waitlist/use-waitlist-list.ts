import { useQuery } from '@tanstack/react-query';

import { fetchWaitlistList, type WaitlistListQuery } from './waitlist-api';
import { waitlistQueryKeys } from './query-keys';

export function useWaitlistList(query: WaitlistListQuery) {
  return useQuery({
    queryKey: waitlistQueryKeys.list(query),
    queryFn: () => fetchWaitlistList(query),
  });
}
