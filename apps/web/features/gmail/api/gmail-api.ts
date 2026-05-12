import { api, xsrfHeader } from '@/lib/api/client';
import { getApiUrl } from '@/lib/api/base-url';

export interface TenantStatus {
  connectionStatus: 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';
  // Mirrors the GmailConnectionStatusResponse wire shape (Phase 1.2.1 D-D4).
}

export interface GetTenantStatusOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
}

export async function getTenantStatus(opts: GetTenantStatusOptions = {}): Promise<TenantStatus> {
  const { fetcher, signal, headers } = opts;
  if (fetcher || headers) {
    const response = await (fetcher ?? fetch)(getApiUrl('/gmail/connection/status'), {
      headers,
      cache: 'no-store',
      signal,
    });
    if (!response.ok) {
      throw new Error(`/gmail/connection/status failed: ${response.status}`);
    }
    return response.json() as Promise<TenantStatus>;
  }

  const { data, error, response } = await api.GET('/gmail/connection/status', { signal });
  if (error || !response.ok)
    throw error ?? new Error(`/gmail/connection/status failed: ${response.status}`);
  return data as TenantStatus;
}

export async function disconnectGmail(): Promise<void> {
  const { error, response } = await api.POST('/tenant/disconnect', {
    headers: { ...xsrfHeader() },
  });
  if (error || !response.ok) {
    throw error ?? new Error(`/tenant/disconnect failed: ${response.status}`);
  }
}
