import { api, xsrfHeader } from '@/lib/api/client';
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
export type TopupIntentRequest = components['schemas']['TopupIntentRequest'];
export type TopupIntentResponse = components['schemas']['TopupIntentResponse'];

export type LedgerHistoryUnavailablePage = {
  unavailable: true;
  entries: [];
  nextCursor: null;
};

export type LedgerHistoryPage = LedgerHistoryUnavailablePage;

function jsonHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json', ...xsrfHeader() };
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
  signal,
}: { signal?: AbortSignal } = {}): Promise<BillingBalanceResponse> {
  const result = await api.GET('/api/billing/balance', { signal });
  return unwrap(result, `/api/billing/balance failed: ${result.response.status}`);
}

export async function createTopupIntent(amountVnd: number): Promise<TopupIntentResponse> {
  const body: TopupIntentRequest = { amountVnd };
  const result = await api.POST('/api/billing/topup/intent', {
    body,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/billing/topup/intent failed: ${result.response.status}`);
}

export async function getLedgerHistory(): Promise<LedgerHistoryPage> {
  // GAP: no backend ledger-history list endpoint as of 05A — see 05A-RESEARCH.md A4.
  // Do NOT call a speculative endpoint or regenerate schema.d.ts here.
  return { unavailable: true, entries: [], nextCursor: null };
}
