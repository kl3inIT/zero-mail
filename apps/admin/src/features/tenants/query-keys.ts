import type { TenantDetailTab, TenantListFilters } from './tenants-api';

export const tenantQueryKeys = {
  all: ['tenants'] as const,
  lists: () => [...tenantQueryKeys.all, 'list'] as const,
  list: (filters: TenantListFilters) => [...tenantQueryKeys.lists(), filters] as const,
  details: () => [...tenantQueryKeys.all, 'detail'] as const,
  detail: (tenantId: string) => [...tenantQueryKeys.details(), tenantId] as const,
  tab: (tenantId: string, tab: TenantDetailTab) => [...tenantQueryKeys.detail(tenantId), tab] as const,
  deletionPreview: (tenantId: string) => [...tenantQueryKeys.detail(tenantId), 'deletion-preview'] as const,
};
