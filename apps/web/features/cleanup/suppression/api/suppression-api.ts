import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type SuppressionEntryResponse = components['schemas']['SuppressionEntryResponse'];
export type SuppressionListResponse = components['schemas']['SuppressionListResponse'];
export type SuppressionAddRequest = components['schemas']['SuppressionAddRequest'];

function jsonHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json', ...xsrfHeader() };
}

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function fetchSuppressionList(): Promise<SuppressionEntryResponse[]> {
  const result = await api.GET('/api/cleanup/suppression', {});
  const data = unwrap(result, `/api/cleanup/suppression failed: ${result.response.status}`);
  // Spec returns SuppressionListResponse { items: [...] } in OpenAPI, but Playwright fixtures
  // sometimes return the bare array. Handle both shapes defensively.
  if (Array.isArray(data)) {
    return data as SuppressionEntryResponse[];
  }
  return data.items ?? [];
}

export async function addSuppression(
  body: SuppressionAddRequest,
): Promise<SuppressionEntryResponse> {
  const result = await api.POST('/api/cleanup/suppression', {
    body,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/cleanup/suppression POST failed: ${result.response.status}`);
}

export async function removeSuppression(suppressionId: string): Promise<void> {
  const result = await api.DELETE('/api/cleanup/suppression/{suppressionId}', {
    params: { path: { suppressionId } },
    headers: xsrfHeader(),
  });
  if (result.error || !result.response.ok) {
    throw (
      result.error ??
      new Error(
        `/api/cleanup/suppression/${suppressionId} DELETE failed: ${result.response.status}`,
      )
    );
  }
}
