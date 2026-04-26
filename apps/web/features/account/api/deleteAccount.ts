import { api, xsrfHeader } from '@/lib/api/client';

export async function deleteAccount(): Promise<void> {
  const { error, response } = await api.DELETE('/me/account', {
    headers: { ...xsrfHeader() },
  });
  if (error || !response.ok)
    throw error ?? new Error(`/me/account DELETE failed: ${response.status}`);
}
