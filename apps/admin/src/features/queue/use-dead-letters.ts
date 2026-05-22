import { useQuery } from '@tanstack/react-query';

import { fetchDeadLetters } from './queue-api';
import { queueQueryKeys } from './query-keys';

export function useDeadLetters(cursor: string | null, limit = 25) {
  return useQuery({
    queryKey: queueQueryKeys.deadLetters(cursor, limit),
    queryFn: () => fetchDeadLetters(cursor, limit),
  });
}
