import { adminXsrfHeader, api } from '@/lib/api/admin-client';
import { getAdminApiUrl } from '@/lib/api/admin-base-url';
import type { components, paths } from '@/lib/api/admin-schema';

export type AdminReferralCampaignResponse =
  components['schemas']['AdminReferralCampaignResponse'];
export type AdminReferralCampaignListResponse =
  components['schemas']['AdminReferralCampaignListResponse'];
export type AdminReferralCampaignCreateRequest =
  components['schemas']['AdminReferralCampaignCreateRequest'];
export type AdminReferralCampaignUpdateRequest =
  components['schemas']['AdminReferralCampaignUpdateRequest'];
export type AdminReferralCampaignStatusUpdateRequest =
  components['schemas']['AdminReferralCampaignStatusUpdateRequest'];
export type AdminReferralCampaignStatus = AdminReferralCampaignResponse['status'];
export type AdminReferralDashboardResponse =
  components['schemas']['AdminReferralDashboardResponse'];
export type AdminReferralLeaderboardRowResponse =
  components['schemas']['AdminReferralLeaderboardRowResponse'];
export type AdminReferralTimeSeriesPointResponse =
  components['schemas']['AdminReferralTimeSeriesPointResponse'];

type DashboardQuery = NonNullable<
  paths['/api/admin/referrals/dashboard']['get']['parameters']['query']
>;

export type ReferralDashboardQueryInput = {
  campaignId: string;
  from: string;
  to: string;
};

export type ReferralDashboardStreamSubscription = {
  close: () => void;
};

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

async function unwrapJsonResponse<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (response.ok) {
    return (await response.json()) as T;
  }

  try {
    throw await response.json();
  } catch (errorPayload) {
    if (errorPayload instanceof SyntaxError) {
      throw new Error(fallbackMessage, { cause: errorPayload });
    }
    throw errorPayload;
  }
}

function toDashboardQuery(input: ReferralDashboardQueryInput): DashboardQuery {
  return {
    campaignId: input.campaignId,
    from: input.from,
    to: input.to,
  };
}

export async function fetchReferralCampaigns(): Promise<AdminReferralCampaignListResponse> {
  const result = await api.GET('/api/admin/referrals/campaigns');
  return unwrap(result, `referral campaigns failed: ${result.response.status}`);
}

export async function createReferralCampaign(
  request: AdminReferralCampaignCreateRequest,
): Promise<AdminReferralCampaignResponse> {
  const result = await api.POST('/api/admin/referrals/campaigns', { body: request });
  return unwrap(result, `create referral campaign failed: ${result.response.status}`);
}

export async function updateReferralCampaign(input: {
  campaignId: string;
  request: AdminReferralCampaignUpdateRequest;
}): Promise<AdminReferralCampaignResponse> {
  const result = await api.PUT('/api/admin/referrals/campaigns/{campaignId}', {
    params: { path: { campaignId: input.campaignId } },
    body: input.request,
  });
  return unwrap(result, `update referral campaign failed: ${result.response.status}`);
}

export async function updateReferralCampaignStatus(input: {
  campaignId: string;
  status: AdminReferralCampaignStatus;
}): Promise<AdminReferralCampaignResponse> {
  const request: AdminReferralCampaignStatusUpdateRequest = { status: input.status };
  const result = await api.PATCH('/api/admin/referrals/campaigns/{campaignId}/status', {
    params: { path: { campaignId: input.campaignId } },
    body: request,
  });
  return unwrap(result, `update referral campaign status failed: ${result.response.status}`);
}

export async function uploadReferralCampaignBanner(input: {
  campaignId: string;
  file: File;
}): Promise<AdminReferralCampaignResponse> {
  const formData = new FormData();
  formData.set('file', input.file);
  const response = await fetch(
    getAdminApiUrl(`/api/admin/referrals/campaigns/${input.campaignId}/banner`),
    {
      method: 'PUT',
      credentials: 'include',
      headers: adminXsrfHeader(),
      body: formData,
    },
  );
  return unwrapJsonResponse(
    response,
    `upload referral campaign banner failed: ${response.status}`,
  );
}

export function referralCampaignBannerUrl(campaign: AdminReferralCampaignResponse): string {
  const url = new URL(
    getAdminApiUrl(`/api/admin/referrals/campaigns/${campaign.campaignId}/banner`),
  );
  url.searchParams.set('v', campaign.updatedAt);
  return url.toString();
}

export async function fetchReferralDashboard(
  input: ReferralDashboardQueryInput,
): Promise<AdminReferralDashboardResponse> {
  const result = await api.GET('/api/admin/referrals/dashboard', {
    params: { query: toDashboardQuery(input) },
  });
  return unwrap(result, `referral dashboard failed: ${result.response.status}`);
}

export function subscribeReferralDashboard(
  input: ReferralDashboardQueryInput,
  onMessage: (dashboard: AdminReferralDashboardResponse) => void,
  onError?: () => void,
): ReferralDashboardStreamSubscription {
  const url = new URL(getAdminApiUrl('/api/admin/referrals/stream'));
  url.searchParams.set('campaignId', input.campaignId);
  url.searchParams.set('from', input.from);
  url.searchParams.set('to', input.to);

  const eventSource = new EventSource(url.toString(), { withCredentials: true });
  eventSource.addEventListener('dashboard', (event) => {
    onMessage(JSON.parse(event.data) as AdminReferralDashboardResponse);
  });
  eventSource.onerror = () => {
    onError?.();
  };
  return {
    close: () => eventSource.close(),
  };
}
