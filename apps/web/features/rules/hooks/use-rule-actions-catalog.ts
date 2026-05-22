'use client';

import { useQuery } from '@tanstack/react-query';

import { listRuleCatalogActions, toRuleCatalogLocale } from '@/features/rules/api/rule-catalog-api';
import { rulesKeys } from '@/features/rules/query-keys';

export function useRuleActionsCatalog(locale: string) {
  const catalogLocale = toRuleCatalogLocale(locale);

  return useQuery({
    queryKey: rulesKeys.catalogActions(catalogLocale),
    queryFn: () => listRuleCatalogActions(catalogLocale),
  });
}
