import { useQuery } from '@tanstack/react-query';

import { fetchMasterKey, fetchMasterKeys } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useMasterKeys() {
  return useQuery({
    queryKey: masterKeyQueryKeys.all,
    queryFn: fetchMasterKeys,
  });
}

export function useMasterKey(provider: string) {
  return useQuery({
    queryKey: masterKeyQueryKeys.detail(provider),
    queryFn: () => fetchMasterKey(provider),
  });
}
