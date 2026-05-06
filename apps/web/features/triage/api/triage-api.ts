import { api, xsrfHeader } from '@/lib/api/client';

export async function setTriagePaused(paused: boolean): Promise<void> {
  const { error, response } = await api.PUT('/tenant/triage-pause', {
    body: { paused },
    headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
  });
  if (error || !response.ok)
    throw error ?? new Error(`/tenant/triage-pause failed: ${response.status}`);
}
