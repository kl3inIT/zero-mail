import { useQuery } from '@tanstack/react-query';

import {
  fetchTenantActivity,
  fetchTenantBilling,
  fetchTenantDeletionPreview,
  fetchTenantHealth,
  fetchTenantOverview,
  fetchTenantSpend,
  type TenantDetailTab,
} from './tenants-api';
import { tenantQueryKeys } from './query-keys';

type TenantTabQueryOptions = {
  enabled: boolean;
};

const tabQueryOptions = {
  staleTime: 0,
  refetchOnMount: 'always' as const,
};

export function useTenantOverview(tenantId: string, options: TenantTabQueryOptions) {
  return useQuery({
    queryKey: tenantQueryKeys.tab(tenantId, 'overview'),
    queryFn: () => fetchTenantOverview(tenantId),
    enabled: options.enabled,
    ...tabQueryOptions,
  });
}

export function useTenantHealth(tenantId: string, options: TenantTabQueryOptions) {
  return useQuery({
    queryKey: tenantQueryKeys.tab(tenantId, 'health'),
    queryFn: () => fetchTenantHealth(tenantId),
    enabled: options.enabled,
    ...tabQueryOptions,
  });
}

export function useTenantBilling(tenantId: string, options: TenantTabQueryOptions) {
  return useQuery({
    queryKey: tenantQueryKeys.tab(tenantId, 'billing'),
    queryFn: () => fetchTenantBilling(tenantId),
    enabled: options.enabled,
    ...tabQueryOptions,
  });
}

export function useTenantSpend(tenantId: string, options: TenantTabQueryOptions) {
  return useQuery({
    queryKey: tenantQueryKeys.tab(tenantId, 'spend'),
    queryFn: () => fetchTenantSpend(tenantId),
    enabled: options.enabled,
    ...tabQueryOptions,
  });
}

export function useTenantActivity(tenantId: string, options: TenantTabQueryOptions) {
  return useQuery({
    queryKey: tenantQueryKeys.tab(tenantId, 'activity'),
    queryFn: () => fetchTenantActivity(tenantId),
    enabled: options.enabled,
    ...tabQueryOptions,
  });
}

export function useTenantDeletionPreview(tenantId: string, enabled: boolean) {
  return useQuery({
    queryKey: tenantQueryKeys.deletionPreview(tenantId),
    queryFn: () => fetchTenantDeletionPreview(tenantId),
    enabled,
    staleTime: 0,
  });
}

export const tenantDetailTabs: TenantDetailTab[] = ['overview', 'health', 'billing', 'spend', 'activity'];
