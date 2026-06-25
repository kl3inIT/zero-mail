import { getApiUrl, getPublicApiUrl } from '@/lib/api/base-url';
import type { components } from '@/lib/api/schema';

// Public, unauthenticated landing endpoint. Read via raw fetch (not the openapi-fetch `api` client)
// because this runs in an RSC and must fail soft — any error resolves to { active: false } so the
// marketing section just hides. Shape comes from the generated schema, no hand-written mirror DTO.
export type ActiveReferralCampaign = components['schemas']['ReferralActiveCampaignResponse'];

const INACTIVE: ActiveReferralCampaign = { active: false };

export interface FetchActiveReferralCampaignOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
}

/**
 * Reads the public marketing-landing referral banner. Isomorphic (RSC + client) and resilient: any
 * failure — backend down, e2e mode, non-OK — resolves to {@code { active: false }} so the landing
 * section simply hides instead of throwing. Never sends credentials; the endpoint exposes only
 * admin-published campaign copy.
 */
export async function fetchActiveReferralCampaign(
  options: FetchActiveReferralCampaignOptions = {},
): Promise<ActiveReferralCampaign> {
  // Server-side Playwright short-circuit — mirrors features/account/api/account-api.ts so the
  // default e2e config (no Spring backend) never logs ECONNREFUSED from Next's fetch reporter.
  if (typeof window === 'undefined' && process.env.ZM_E2E === '1') {
    return INACTIVE;
  }
  const fetcher = options.fetcher ?? fetch;
  try {
    const response = await fetcher(getApiUrl('/api/referrals/active-campaign'), {
      cache: 'no-store',
      signal: options.signal,
    });
    if (!response.ok) return INACTIVE;
    return (await response.json()) as ActiveReferralCampaign;
  } catch {
    return INACTIVE;
  }
}

/** Browser-facing URL for the active campaign's banner image (served by ReferralPublicController). */
export function activeReferralBannerUrl(): string {
  return getPublicApiUrl('/api/referrals/active-campaign/banner');
}
