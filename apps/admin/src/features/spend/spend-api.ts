import { api } from '@/lib/api/admin-client';
import { getAdminApiUrl } from '@/lib/api/admin-base-url';
import type { components } from '@/lib/api/admin-schema';

export type SpendKpiResponse = components['schemas']['SpendKpiResponse'];
export type ProviderStackBarRowResponse = components['schemas']['ProviderStackBarRowResponse'];
export type FeatureDonutSliceResponse = components['schemas']['FeatureDonutSliceResponse'];
export type TopTenantRowResponse = components['schemas']['TopTenantRowResponse'];
export type SpendDashboardResponse = components['schemas']['SpendDashboardResponse'];

export type SpendQueryInput = {
  from: Date;
  to: Date;
  providers?: string[];
  features?: string[];
};

/** Maximum spend-range window in milliseconds (90 days). */
export const SPEND_MAX_RANGE_MS = 90 * 24 * 60 * 60 * 1000;

export function isRangeWithinLimit(input: { from: Date; to: Date }): boolean {
  return input.to.getTime() - input.from.getTime() <= SPEND_MAX_RANGE_MS;
}

type ProviderEnum = NonNullable<components['schemas']['SetFeatureDefaultRequest']['provider']>;
type FeatureEnum = NonNullable<components['schemas']['SetFeatureDefaultRequest']['feature']>;

function spendQuery(input: SpendQueryInput) {
  return {
    from: input.from.toISOString(),
    to: input.to.toISOString(),
    providers:
      input.providers && input.providers.length > 0
        ? (input.providers as ProviderEnum[])
        : undefined,
    features:
      input.features && input.features.length > 0
        ? (input.features as FeatureEnum[])
        : undefined,
  };
}

export async function fetchSpendDashboard(
  input: SpendQueryInput,
): Promise<SpendDashboardResponse> {
  const { data, error } = await api.GET('/api/admin/spend/dashboard', {
    params: { query: spendQuery(input) },
  });
  if (error || !data) {
    throw new Error('Không thể tải dashboard chi tiêu.');
  }
  return data;
}

// CSV download stays on raw fetch — openapi-fetch is JSON-only and the blob
// + anchor click pattern needs direct Response access. The path mirrors
// fetchSpendDashboard so the schema regen above still documents it correctly.
export async function downloadSpendCsv(input: SpendQueryInput): Promise<void> {
  const params = new URLSearchParams();
  params.set('from', input.from.toISOString());
  params.set('to', input.to.toISOString());
  if (input.providers && input.providers.length > 0) {
    params.set('providers', input.providers.join(','));
  }
  if (input.features && input.features.length > 0) {
    params.set('features', input.features.join(','));
  }
  const response = await fetch(
    getAdminApiUrl(`/api/admin/spend/dashboard/csv?${params.toString()}`),
    {
      credentials: 'include',
      headers: { Accept: 'text/csv' },
    },
  );
  if (!response.ok) {
    throw new Error(`Xuất CSV thất bại: ${response.status} ${response.statusText}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `spend-${input.from.toISOString().slice(0, 10)}-${input.to
    .toISOString()
    .slice(0, 10)}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
