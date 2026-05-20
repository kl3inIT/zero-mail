import { useMutation } from '@tanstack/react-query';

import { startCatalogSync, type CatalogProvider } from './catalog-api';

export function useSyncFetch() {
  return useMutation({
    mutationFn: (provider: CatalogProvider) => startCatalogSync(provider),
  });
}
