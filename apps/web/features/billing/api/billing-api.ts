import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

/**
 * Credit and plan-upgrade API surface:
 * - GET /api/credits/balance returns plan allowance credit summary metadata.
 * - GET /api/credits/ledger returns recent credit activity.
 * - GET /api/plan-upgrades/plans returns active plans and feature list.
 * - POST /api/plan-upgrades/plans/{planCode}/checkout creates a hosted checkout URL.
 */

export type BillingBalanceResponse = components['schemas']['BillingBalanceResponse'];
export type BillingCheckoutResponse = components['schemas']['BillingCheckoutResponse'];
export type BillingLedgerEntryResponse = components['schemas']['BillingLedgerEntryResponse'];
export type BillingLedgerHistoryResponse = components['schemas']['BillingLedgerHistoryResponse'];
export type BillingPlanResponse = components['schemas']['BillingPlanResponse'];
export type BillingPlanListResponse = components['schemas']['BillingPlanListResponse'];
export type PlanFeatureSummaryResponse = components['schemas']['PlanFeatureSummaryResponse'];

export type LedgerHistoryPage = Omit<BillingLedgerHistoryResponse, 'nextCursor'> & {
  nextCursor: string | null;
};

export interface BillingBalanceOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
}

export interface BillingPlansOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
}

export interface LedgerHistoryOptions {
  limit?: number;
  cursor?: string | null;
  signal?: AbortSignal;
}

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function getBillingBalance({
  fetcher,
  signal,
  headers,
}: BillingBalanceOptions = {}): Promise<BillingBalanceResponse> {
  const result = await api.GET('/api/credits/balance', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });
  return unwrap(result, `/api/credits/balance failed: ${result.response.status}`);
}

export async function getLedgerHistory({
  limit = 10,
  cursor,
  signal,
}: LedgerHistoryOptions = {}): Promise<LedgerHistoryPage> {
  const result = await api.GET('/api/credits/ledger', {
    params: { query: cursor ? { limit, cursor } : { limit } },
    signal,
  });
  const response = unwrap(result, `/api/credits/ledger failed: ${result.response.status}`);
  return {
    entries: response.entries,
    nextCursor: response.nextCursor ?? null,
  };
}

export async function getBillingPlans({
  fetcher,
  signal,
  headers,
}: BillingPlansOptions = {}): Promise<BillingPlanListResponse> {
  const result = await api.GET('/api/plan-upgrades/plans', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });
  return unwrap(result, `/api/plan-upgrades/plans failed: ${result.response.status}`);
}

export async function createBillingCheckout(
  planCode: BillingPlanResponse['code'],
): Promise<BillingCheckoutResponse> {
  const result = await api.POST('/api/plan-upgrades/plans/{planCode}/checkout', {
    params: { path: { planCode } },
  });
  return unwrap(
    result,
    `/api/plan-upgrades/plans/${planCode}/checkout failed: ${result.response.status}`,
  );
}
