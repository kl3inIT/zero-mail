export const ruleCatalogQueryKeys = {
  all: ['rule-catalog'] as const,
  personas: () => [...ruleCatalogQueryKeys.all, 'personas'] as const,
  actions: () => [...ruleCatalogQueryKeys.all, 'actions'] as const,
};
