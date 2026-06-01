import { api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type ByokResponse = components['schemas']['ByokResponse'];
export type ByokSaveRequest = components['schemas']['ByokSaveRequest'];
export type ByokProvider = ByokSaveRequest['provider'];
export type ByokTestConnectionResponse = components['schemas']['ByokTestConnectionResponse'];
export type ByokTestResult = ByokTestConnectionResponse['result'];
export type AiCostResponse = components['schemas']['AiCostResponse'];

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function getByok(): Promise<ByokResponse | null> {
  const result = await api.GET('/api/byok', {});
  if (result.response.status === 404) return null;
  return unwrap(result, `/api/byok failed: ${result.response.status}`);
}

export async function saveByok(body: ByokSaveRequest): Promise<ByokResponse> {
  const result = await api.POST('/api/byok', { body });
  return unwrap(result, `/api/byok save failed: ${result.response.status}`);
}

export async function testByokConnection(): Promise<ByokTestConnectionResponse> {
  const result = await api.POST('/api/byok/test-connection', {});
  return unwrap(result, `/api/byok/test-connection failed: ${result.response.status}`);
}

export async function selectByokModel(modelId: string): Promise<ByokResponse> {
  const result = await api.PUT('/api/byok/model', { body: { modelId } });
  return unwrap(result, `/api/byok/model failed: ${result.response.status}`);
}

export async function activateByok(active: boolean): Promise<ByokResponse> {
  const result = await api.PUT('/api/byok/active', { body: { active } });
  return unwrap(result, `/api/byok/active failed: ${result.response.status}`);
}

export async function deleteByok(): Promise<void> {
  const result = await api.DELETE('/api/byok', {});
  if (result.error || !result.response.ok) {
    throw result.error ?? new Error(`/api/byok delete failed: ${result.response.status}`);
  }
}

export async function getAiCost(window = '7d'): Promise<AiCostResponse> {
  const result = await api.GET('/api/settings/ai/cost', {
    params: { query: { window } },
  });
  return unwrap(result, `/api/settings/ai/cost failed: ${result.response.status}`);
}
