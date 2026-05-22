import type { CatalogProvider } from './catalog-api';

export const catalogQueryKeys = {
  all: ['catalog'] as const,
  provider: (provider: CatalogProvider) => [...catalogQueryKeys.all, 'provider', provider] as const,
  sync: () => [...catalogQueryKeys.all, 'sync'] as const,
  syncJob: (jobId: string) => [...catalogQueryKeys.sync(), jobId] as const,
};
