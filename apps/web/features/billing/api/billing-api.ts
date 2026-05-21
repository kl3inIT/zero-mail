import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

/**
 * Billing API surface as of Phase 05A:
 * - GET /api/billing/balance exists.
 * - POST /api/billing/topup/intent exists.
 * - GAP: no backend ledger-history list endpoint as of 05A — see 05A-RESEARCH.md A4.
 * - GAP: no top-up intent-status endpoint or intentId as of 05A — see 05A-RESEARCH.md A6.
 * - GAP: TopupIntentResponse carries no separate bank account/name fields; qrPayload is authoritative.
 */

export type BillingBalanceResponse = components['schemas']['BillingBalanceResponse'];
export type BillingPackageResponse = components['schemas']['BillingPackageResponse'];
export type TopupIntentRequest = components['schemas']['TopupIntentRequest'];
export type TopupIntentResponse = components['schemas']['TopupIntentResponse'];

export type LedgerHistoryUnavailablePage = {
  unavailable: true;
  entries: [];
  nextCursor: null;
};

export type LedgerHistoryPage = LedgerHistoryUnavailablePage;

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

export async function listBillingPackages(): Promise<BillingPackageResponse[]> {
  const result = await api.GET('/api/billing/packages', {});
  return unwrap(result, `/api/billing/packages failed: ${result.response.status}`);
}

export async function createTopupIntent(packageCode: string): Promise<TopupIntentResponse> {
  const body: TopupIntentRequest = { packageCode };
  const result = await api.POST('/api/billing/topup/intent', {
    body,
  });
  return unwrap(result, `/api/billing/topup/intent failed: ${result.response.status}`);
}

export async function getLedgerHistory(): Promise<LedgerHistoryPage> {
  // GAP: no backend ledger-history list endpoint as of 05A — see 05A-RESEARCH.md A4.
  // Do NOT call a speculative endpoint or regenerate schema.d.ts here.
  return { unavailable: true, entries: [], nextCursor: null };
}
