import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type UnsubscribeCandidateResponse = components['schemas']['UnsubscribeCandidateResponse'];
export type UnsubscribeCandidateListResponse =
  components['schemas']['UnsubscribeCandidateListResponse'];
export type CampaignPreviewRequest = components['schemas']['CampaignPreviewRequest'];
export type CampaignPreviewResponse = components['schemas']['CampaignPreviewResponse'];
export type PerSenderPreviewResponse = components['schemas']['PerSenderPreviewResponse'];
export type CampaignExecuteRequest = components['schemas']['CampaignExecuteRequest'];
export type CampaignExecuteResponse = components['schemas']['CampaignExecuteResponse'];
export type CampaignStatusResponse = components['schemas']['CampaignStatusResponse'];
export type PerSenderStateResponse = components['schemas']['PerSenderStateResponse'];

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

export async function fetchCandidates(
  window: string = '30d',
  limit: number = 25,
): Promise<UnsubscribeCandidateResponse[]> {
  const result = await api.GET('/api/unsubscribe/candidates', {
    params: { query: { window, limit } },
  });
  const data = unwrap(result, `/api/unsubscribe/candidates failed: ${result.response.status}`);
  // Defensively unwrap either { items: [...] } or bare array (Playwright fixtures use the bare
  // array form).
  if (Array.isArray(data)) {
    return data as UnsubscribeCandidateResponse[];
  }
  return data.items ?? [];
}

export async function previewCampaign(
  body: CampaignPreviewRequest,
): Promise<CampaignPreviewResponse> {
  const result = await api.POST('/api/unsubscribe/campaigns/preview', {
    body,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/unsubscribe/campaigns/preview failed: ${result.response.status}`);
}

export async function executeCampaign(
  body: CampaignExecuteRequest,
): Promise<CampaignExecuteResponse> {
  const result = await api.POST('/api/unsubscribe/campaigns/execute', {
    body,
    headers: jsonHeaders(),
  });
  return unwrap(result, `/api/unsubscribe/campaigns/execute failed: ${result.response.status}`);
}

export async function fetchCampaignStatus(jobId: string): Promise<CampaignStatusResponse> {
  const result = await api.GET('/api/unsubscribe/campaigns/{jobId}', {
    params: { path: { jobId } },
  });
  return unwrap(result, `/api/unsubscribe/campaigns/${jobId} failed: ${result.response.status}`);
}

export async function retrySender(jobId: string, senderEmail: string): Promise<void> {
  const result = await api.POST('/api/unsubscribe/campaigns/{jobId}/senders/{senderEmail}/retry', {
    params: { path: { jobId, senderEmail } },
    headers: xsrfHeader(),
  });
  if (result.error || !result.response.ok) {
    throw (
      result.error ??
      new Error(
        `/api/unsubscribe/campaigns/${jobId}/senders/${senderEmail}/retry failed: ${result.response.status}`,
      )
    );
  }
}

export async function undoCampaign(jobId: string): Promise<void> {
  const result = await api.POST('/api/unsubscribe/campaigns/{jobId}/undo', {
    params: { path: { jobId } },
    headers: xsrfHeader(),
  });
  if (result.error || !result.response.ok) {
    throw (
      result.error ??
      new Error(`/api/unsubscribe/campaigns/${jobId}/undo failed: ${result.response.status}`)
    );
  }
}
