import { adaptFetchForOpenApi, api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type TenantStatus = components['schemas']['GmailConnectionStatusResponse'];

export interface GetTenantStatusOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
}

export async function getTenantStatus(opts: GetTenantStatusOptions = {}): Promise<TenantStatus> {
  const { fetcher, signal, headers } = opts;
  const { data, error, response } = await api.GET('/gmail/connection/status', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });
  if (error || !response.ok || data === undefined) {
    throw error ?? new Error(`/gmail/connection/status failed: ${response.status}`);
  }
  return data;
}

export async function disconnectGmail(): Promise<void> {
  const { error, response } = await api.POST('/tenant/disconnect', {
    headers: { ...xsrfHeader() },
  });
  if (error || !response.ok) {
    throw error ?? new Error(`/tenant/disconnect failed: ${response.status}`);
  }
}
