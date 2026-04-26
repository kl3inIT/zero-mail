import { api } from '@/lib/api/client';

export interface TenantStatus {
  connectionStatus: 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';
  // Mirrors the GmailConnectionStatusResponse wire shape (Phase 1.2.1 D-D4).
}

export async function getTenantStatus(opts: { signal?: AbortSignal } = {}): Promise<TenantStatus> {
  const { signal } = opts;
  const { data, error, response } = await api.GET('/gmail/connection/status', { signal });
  if (error || !response.ok)
    throw error ?? new Error(`/gmail/connection/status failed: ${response.status}`);
  return data as TenantStatus;
}
