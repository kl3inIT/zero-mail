import { api } from '@/lib/api/admin-client';
import type { components, paths } from '@/lib/api/admin-schema';

export type AdminOverviewResponse = components['schemas']['AdminOverviewResponse'];
export type AdminOverviewDailyActivityPoint =
  components['schemas']['AdminOverviewDailyActivityPointResponse'];
export type AdminOverviewActionDistribution =
  components['schemas']['AdminOverviewActionDistributionResponse'];
export type AdminOverviewTopActivityTenant =
  components['schemas']['AdminOverviewTopActivityTenantResponse'];
export type AdminOverviewTopSpendTenant =
  components['schemas']['AdminOverviewTopSpendTenantResponse'];
export type AdminOverviewAlert = components['schemas']['AdminOverviewAlertResponse'];

type AdminOverviewQuery = NonNullable<
  paths['/api/admin/overview']['get']['parameters']['query']
>;

export type AdminOverviewQueryInput = {
  from: Date;
  to: Date;
};

function toAdminOverviewQuery(input: AdminOverviewQueryInput): AdminOverviewQuery {
  return {
    from: input.from.toISOString(),
    to: input.to.toISOString(),
  };
}

export async function fetchAdminOverview(
  input: AdminOverviewQueryInput,
): Promise<AdminOverviewResponse> {
  const { data, error } = await api.GET('/api/admin/overview', {
    params: { query: toAdminOverviewQuery(input) },
  });
  if (error || !data) {
    throw new Error('Không thể tải dashboard tổng quan.');
  }
  return data;
}
