import { useQuery } from '@tanstack/react-query';

import { fetchMasterKey, fetchMasterKeys, fetchProviderKeys } from './master-keys-api';
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

export function useProviderKeys(provider: string) {
  return useQuery({
    queryKey: masterKeyQueryKeys.keys(provider),
    queryFn: () => fetchProviderKeys(provider),
  });
}
