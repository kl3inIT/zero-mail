import { api, xsrfHeader } from '@/lib/api/client';

export async function completeOnboarding(): Promise<void> {
  const { error, response } = await api.POST('/onboarding/complete', {
    headers: { ...xsrfHeader() },
  });
  if (error || !response.ok)
    throw error ?? new Error(`/onboarding/complete failed: ${response.status}`);
}
