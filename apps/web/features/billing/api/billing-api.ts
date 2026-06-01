import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

/**
 * Credit and plan-upgrade API surface:
 * - GET /api/credits/balance returns plan allowance credit summary metadata.
 * - GET /api/credits/ledger returns recent credit activity.
 * - GET /api/plan-upgrades/plans returns active plans and feature list.
 * - POST /api/plan-upgrades/checkout creates a hosted or bank-transfer checkout.
 */

export type BillingBalanceResponse = components['schemas']['BillingBalanceResponse'];
export type BillingLedgerEntryResponse = components['schemas']['BillingLedgerEntryResponse'];
export type BillingLedgerHistoryResponse = components['schemas']['BillingLedgerHistoryResponse'];
export type BillingPlanResponse = components['schemas']['BillingPlanResponse'];
export type BillingPlanListResponse = components['schemas']['BillingPlanListResponse'];
export type PlanFeatureSummaryResponse = components['schemas']['PlanFeatureSummaryResponse'];
export type BankTransferIntentResponse = components['schemas']['BankTransferIntentResponse'];
export type BillingCheckoutResponse = components['schemas']['BillingCheckoutResponse'];
export type BillingCheckoutRequest = components['schemas']['BillingCheckoutRequest'];
export type BillingPaymentMethod = BillingCheckoutRequest['paymentMethod'];

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
  request: BillingCheckoutRequest,
): Promise<BillingCheckoutResponse> {
  const result = await api.POST('/api/plan-upgrades/checkout', {
    body: request,
  });
  return unwrap(result, `/api/plan-upgrades/checkout failed: ${result.response.status}`);
}

export async function getBankTransferIntent(
  intentId: BankTransferIntentResponse['id'],
): Promise<BankTransferIntentResponse> {
  const result = await api.GET('/api/plan-upgrades/bank-transfer-intents/{intentId}', {
    params: { path: { intentId } },
  });
  return unwrap(
    result,
    `/api/plan-upgrades/bank-transfer-intents/${intentId} failed: ${result.response.status}`,
  );
}
