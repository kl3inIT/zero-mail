import { getApiBase } from '@/lib/api/base-url';

export function getBillingWebSocketUrl(): string {
  const apiBase = new URL(getApiBase());
  apiBase.protocol = apiBase.protocol === 'https:' ? 'wss:' : 'ws:';
  apiBase.pathname = `${apiBase.pathname.replace(/\/$/, '')}/ws`;
  apiBase.search = '';
  apiBase.hash = '';
  return apiBase.toString();
}
