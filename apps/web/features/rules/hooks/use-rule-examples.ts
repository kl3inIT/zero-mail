'use client';

import { useQuery } from '@tanstack/react-query';

import {
  listRuleCatalogExamples,
  toRuleCatalogLocale,
} from '@/features/rules/api/rule-catalog-api';
import { rulesKeys } from '@/features/rules/query-keys';

export function useRuleExamples(locale: string) {
  const catalogLocale = toRuleCatalogLocale(locale);

  return useQuery({
    queryKey: rulesKeys.catalogExamples(catalogLocale),
    queryFn: () => listRuleCatalogExamples(catalogLocale),
  });
}
