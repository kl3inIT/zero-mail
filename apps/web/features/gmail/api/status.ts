import { api } from '@/lib/api/client';

export interface TenantStatus {
  connectionStatus: 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';
  // Mirrors current /tenant/status response shape from existing inline call site.
}

export async function getTenantStatus(opts: { signal?: AbortSignal } = {}): Promise<TenantStatus> {
  const { signal } = opts;
  const { data, error, response } = await api.GET('/tenant/status', { signal });
  if (error || !response.ok) throw error ?? new Error(`/tenant/status failed: ${response.status}`);
  return data as TenantStatus;
}
