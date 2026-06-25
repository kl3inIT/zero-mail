import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { referralKeys } from '@/features/referrals/query-keys';

type UseQueryOptionsForTest = {
  queryKey: readonly unknown[];
  queryFn: (context: { signal?: AbortSignal }) => Promise<unknown>;
  refetchInterval?: number;
  refetchIntervalInBackground?: boolean;
  refetchOnMount?: 'always' | boolean;
  refetchOnWindowFocus?: boolean;
  staleTime?: number;
};

const mocks = vi.hoisted(() => ({
  fetchReferralMe: vi.fn(),
  useQuery: vi.fn(),
}));

vi.mock('@/features/referrals/api/referrals-api', () => ({
  fetchReferralMe: mocks.fetchReferralMe,
}));

vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: UseQueryOptionsForTest) => mocks.useQuery(options),
}));

import {
  REFERRAL_ME_REFETCH_INTERVAL_MS,
  useReferralMe,
} from '@/features/referrals/hooks/use-referral-me';

describe('useReferralMe', () => {
  beforeEach(() => {
    mocks.fetchReferralMe.mockReset();
    mocks.fetchReferralMe.mockResolvedValue({
      active: false,
      url: null,
      code: null,
      campaignId: null,
      campaignName: null,
      campaignDescription: null,
      campaignStartsAt: null,
      campaignEndsAt: null,
      successfulReferrals: 0,
      totalRankedTenants: 0,
      currentTenant: null,
      leaderboard: [],
      snapshotAt: '2026-06-20T03:00:00Z',
    });
    mocks.useQuery.mockReset();
    mocks.useQuery.mockImplementation(() => ({ data: undefined }));
  });

  it('refetches active campaign state instead of reusing the global five minute cache', () => {
    renderHook(() => useReferralMe());

    expect(mocks.useQuery).toHaveBeenCalledWith(
      expect.objectContaining({
        queryKey: referralKeys.me(),
        refetchInterval: REFERRAL_ME_REFETCH_INTERVAL_MS,
        refetchIntervalInBackground: false,
        refetchOnMount: 'always',
        refetchOnWindowFocus: true,
        staleTime: 0,
      }),
    );
  });
});
