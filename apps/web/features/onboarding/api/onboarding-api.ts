import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type SelectTemplateBody = components['schemas']['SelectTemplateRequest'];

export async function selectTemplate(body: SelectTemplateBody): Promise<void> {
  const { error, response } = await api.POST('/onboarding/select-template', {
    body,
    headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
  });
  if (error || !response.ok)
    throw error ?? new Error(`/onboarding/select-template failed: ${response.status}`);
}

export async function completeOnboarding(): Promise<void> {
  const { error, response } = await api.POST('/onboarding/complete', {
    headers: { ...xsrfHeader() },
  });
  if (error || !response.ok) {
    throw error ?? new Error(`/onboarding/complete failed: ${response.status}`);
  }
}
