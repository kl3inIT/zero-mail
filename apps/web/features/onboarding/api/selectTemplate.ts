import { api, xsrfHeader } from '@/lib/api/client';

export interface SelectTemplateBody {
  templateKey: 'archive-receipts' | 'label-newsletters' | 'pin-calendar';
}

export async function selectTemplate(body: SelectTemplateBody): Promise<void> {
  const { error, response } = await api.POST('/onboarding/select-template', {
    body,
    headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
  });
  if (error || !response.ok)
    throw error ?? new Error(`/onboarding/select-template failed: ${response.status}`);
}
