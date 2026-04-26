import { api, xsrfHeader } from '@/lib/api/client';

export async function disconnectGmail(): Promise<void> {
  const { error, response } = await api.POST('/tenant/disconnect', {
    headers: { ...xsrfHeader() },
  });
  if (error || !response.ok)
    throw error ?? new Error(`/tenant/disconnect failed: ${response.status}`);
}
