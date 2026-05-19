import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type ByokValidatePayload = components['schemas']['ByokValidateRequest'];
export type ByokValidateResult = components['schemas']['ByokValidateResponse'];
export type ByokSavePayload = components['schemas']['ByokSaveRequest'];
export type ByokSaveResult = components['schemas']['ByokSaveResponse'];
export type ByokCurrentResult = components['schemas']['ByokCurrentResponse'];
export type ByokProviderPreset = ByokValidatePayload['preset'];
export type ByokProvider = NonNullable<ByokCurrentResult['provider']>;

export async function validateByok(payload: ByokValidatePayload): Promise<ByokValidateResult> {
  const { data, error, response } = await api.POST('/api/llm/byok/validate', {
    body: payload,
    headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
  });
  if (error || !response.ok || data === undefined)
    throw error ?? new Error(`/api/llm/byok/validate failed: ${response.status}`);
  return data;
}

export async function saveByok(payload: ByokSavePayload): Promise<ByokSaveResult> {
  const { data, error, response } = await api.POST('/api/llm/byok', {
    body: payload,
    headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
  });
  if (error || !response.ok || data === undefined)
    throw error ?? new Error(`/api/llm/byok save failed: ${response.status}`);
  return data;
}

export async function getCurrentByok(): Promise<ByokCurrentResult | null> {
  const { data, error, response } = await api.GET('/api/llm/byok', {});
  if (response.status === 204 || data === null) return null;
  if (error || !response.ok || data === undefined)
    throw error ?? new Error(`/api/llm/byok current failed: ${response.status}`);

  if (!data.provider || !data.savedAt) return null;
  return data;
}
