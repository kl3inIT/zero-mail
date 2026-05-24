import { useQuery } from '@tanstack/react-query';

import { fetchCatalogProvider, type CatalogProvider } from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export function useCatalog(provider: CatalogProvider) {
  return useQuery({
    queryKey: catalogQueryKeys.provider(provider),
    queryFn: () => fetchCatalogProvider(provider),
    enabled: provider.length > 0,
  });
}
