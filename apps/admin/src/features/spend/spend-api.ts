// Spend feature uses raw fetch because /api/admin/spend/** is not yet in the regenerated
// openapi-typescript schema (`apps/admin/src/lib/api/admin-schema.d.ts`). Per CLAUDE.md
// Convention 8, raw fetch is allowed "temporarily missing schema with an explicit TODO".
// TODO(08-8F follow-up): run `pnpm --filter @zeromail/admin generate-api` against a running
// backend at localhost:8080 and migrate this file to the typed `api.GET / api.POST` client.

import { getAdminApiUrl } from '@/lib/api/admin-base-url';

export type SpendKpiResponse = {
  todayPlatformCost: string;
  todayByokCost: string;
  todayUnknownCost: string;
  sevenDayPlatformCost: string;
  sevenDayByokCost: string;
  sevenDayUnknownCost: string;
  thirtyDayPlatformCost: string;
  thirtyDayByokCost: string;
  thirtyDayUnknownCost: string;
  todayCallCount: number;
  sevenDayCallCount: number;
  thirtyDayCallCount: number;
};

export type ProviderStackBarRowResponse = {
  bucketDate: string;
  provider: string;
  platformCost: string;
  byokCost: string;
  unknownCost: string;
  callCount: number;
};

export type FeatureDonutSliceResponse = {
  feature: string;
  totalCost: string;
  callCount: number;
  percentOfTotal: number;
};

export type TopTenantRowResponse = {
  tenantId: string | null;
  gmailAccountEmailOrPlaceholder: string;
  totalCost: string;
  unknownCost: string;
  callCount: number;
  isKAnonymized: boolean;
  unknownPct: number;
};

export type SpendDashboardResponse = {
  kpis: SpendKpiResponse;
  stackBar: ProviderStackBarRowResponse[];
  donut: FeatureDonutSliceResponse[];
  topTenants: TopTenantRowResponse[];
  snapshotAt: string;
  kAnonymityFooterNote: string;
  unknownPercentOfTotal: number;
  rowLevelClassificationSince: string;
};

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

async function fetchJson<T>(path: `/${string}`, init?: RequestInit): Promise<T> {
  const response = await fetch(getAdminApiUrl(path), {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!response.ok) {
    throw new Error(`Yêu cầu thất bại: ${response.status} ${response.statusText}`);
  }
  return (await response.json()) as T;
}

function buildSpendParams(input: SpendQueryInput): string {
  const params = new URLSearchParams();
  params.set('from', input.from.toISOString());
  params.set('to', input.to.toISOString());
  if (input.providers && input.providers.length > 0) {
    params.set('providers', input.providers.join(','));
  }
  if (input.features && input.features.length > 0) {
    params.set('features', input.features.join(','));
  }
  return params.toString();
}

export async function fetchSpendDashboard(
  input: SpendQueryInput,
): Promise<SpendDashboardResponse> {
  return fetchJson<SpendDashboardResponse>(
    `/api/admin/spend/dashboard?${buildSpendParams(input)}`,
  );
}

export async function downloadSpendCsv(input: SpendQueryInput): Promise<void> {
  const response = await fetch(
    getAdminApiUrl(`/api/admin/spend/dashboard/csv?${buildSpendParams(input)}`),
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
