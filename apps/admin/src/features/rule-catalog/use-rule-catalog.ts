import { useQuery } from '@tanstack/react-query';

import { fetchRuleCatalogActions, fetchRuleCatalogPersonas } from './rule-catalog-api';
import { ruleCatalogQueryKeys } from './query-keys';

export function useRuleCatalogPersonas() {
  return useQuery({
    queryKey: ruleCatalogQueryKeys.personas(),
    queryFn: fetchRuleCatalogPersonas,
  });
}

export function useRuleCatalogActions() {
  return useQuery({
    queryKey: ruleCatalogQueryKeys.actions(),
    queryFn: fetchRuleCatalogActions,
  });
}
