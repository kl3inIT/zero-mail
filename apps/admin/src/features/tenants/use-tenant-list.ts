import { useQuery } from '@tanstack/react-query';

import { fetchTenantList, type TenantListFilters } from './tenants-api';
import { tenantQueryKeys } from './query-keys';

export function useTenantList(filters: TenantListFilters, enabled = true) {
  return useQuery({
    queryKey: tenantQueryKeys.list(filters),
    queryFn: () => fetchTenantList(filters),
    enabled,
  });
}
