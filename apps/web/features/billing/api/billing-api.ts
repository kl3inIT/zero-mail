import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

/**
 * Billing API surface:
 * - GET /api/billing/balance returns beta-aware credit summary metadata.
 * - GET /api/billing/ledger returns recent credit activity.
 * - GET /api/billing/plans returns active billing plans and feature list.
 * - POST /api/billing/plans/{planCode}/checkout creates a hosted checkout URL on demand.
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
  const result = await api.GET('/api/billing/balance', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });
  return unwrap(result, `/api/billing/balance failed: ${result.response.status}`);
}

export async function getLedgerHistory(limit = 50): Promise<LedgerHistoryPage> {
  const result = await api.GET('/api/billing/ledger', {
    params: { query: { limit } },
  });
  const response = unwrap(result, `/api/billing/ledger failed: ${result.response.status}`);
  return {
    entries: response.entries,
    nextCursor: response.nextCursor ?? null,
  };
}

export async function getBillingPlans(): Promise<BillingPlanListResponse> {
  const result = await api.GET('/api/billing/plans', {});
  return unwrap(result, `/api/billing/plans failed: ${result.response.status}`);
}

export async function createBillingCheckout(
  planCode: BillingPlanResponse['code'],
): Promise<BillingCheckoutResponse> {
  const result = await api.POST('/api/billing/plans/{planCode}/checkout', {
    params: { path: { planCode } },
  });
  return unwrap(
    result,
    `/api/billing/plans/${planCode}/checkout failed: ${result.response.status}`,
  );
}
