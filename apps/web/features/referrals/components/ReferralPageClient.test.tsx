import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ReferralPageClient } from './ReferralPageClient';

const mocks = vi.hoisted(() => ({
  endCampaignIfExpired: vi.fn(),
  useEndReferralCampaignIfExpired: vi.fn(),
  useReferralMe: vi.fn(),
}));

vi.mock('@/features/referrals/hooks/use-referral-me', () => ({
  useReferralMe: mocks.useReferralMe,
}));

vi.mock('@/features/referrals/hooks/use-end-referral-campaign-if-expired', () => ({
  useEndReferralCampaignIfExpired: mocks.useEndReferralCampaignIfExpired,
}));

vi.mock('@/lib/api/base-url', () => ({
  getPublicApiUrl: (path: string) => `https://api.zeromail.test${path}`,
}));

describe('ReferralPageClient ended event state', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: new Date('2026-09-04T00:00:00Z') });
    mocks.endCampaignIfExpired.mockReset();
    mocks.useEndReferralCampaignIfExpired.mockReset();
    mocks.useEndReferralCampaignIfExpired.mockReturnValue({
      mutate: mocks.endCampaignIfExpired,
    });
    mocks.useReferralMe.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders the final top-three result when the campaign countdown has ended', () => {
    mocks.useReferralMe.mockReturnValue({
      isLoading: false,
      data: referralResponse({
        currentTenant: leaderboardRow({ rank: 2, successfulReferrals: 24, currentTenant: true }),
      }),
    });

    render(<ReferralPageClient />);

    expect(screen.getAllByText('Đã kết thúc')[0]).toBeInTheDocument();
    expect(screen.getByText('Bảng xếp hạng cuối cùng')).toBeInTheDocument();
    expect(screen.getByText(/Chúc mừng/)).toBeInTheDocument();
    expect(screen.getByText('TOP 2')).toBeInTheDocument();
    expect(screen.getByText(/Thông báo nhận thưởng/)).toBeInTheDocument();
    expect(screen.queryByText('Đếm ngược')).not.toBeInTheDocument();
  });

  it('does not render the share link after the campaign has ended', () => {
    mocks.useReferralMe.mockReturnValue({
      isLoading: false,
      data: referralResponse({
        currentTenant: leaderboardRow({ rank: 2, successfulReferrals: 24, currentTenant: true }),
      }),
    });

    render(<ReferralPageClient />);

    expect(
      screen.queryByDisplayValue('https://zeromail.vn/referral?code=DTH12345'),
    ).not.toBeInTheDocument();
  });

  it('renders the final participation result when the tenant is outside the top three', () => {
    mocks.useReferralMe.mockReturnValue({
      isLoading: false,
      data: referralResponse({
        currentTenant: leaderboardRow({ rank: 7, successfulReferrals: 18, currentTenant: true }),
      }),
    });

    render(<ReferralPageClient />);

    expect(screen.getAllByText('Đã kết thúc')[0]).toBeInTheDocument();
    expect(screen.getByText('Cảm ơn bạn đã tham gia')).toBeInTheDocument();
    expect(screen.getByText('Rất tiếc, bạn chưa nằm trong TOP 3 lần này.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Theo dõi sự kiện mới' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Xem lịch sử sự kiện' })).toBeInTheDocument();
  });

  it('triggers backend expiry once when the countdown reaches zero', async () => {
    vi.setSystemTime(new Date('2026-09-03T23:58:58Z'));
    mocks.useReferralMe.mockReturnValue({
      isLoading: false,
      data: referralResponse({
        currentTenant: leaderboardRow({ rank: 2, successfulReferrals: 24, currentTenant: true }),
      }),
    });

    render(<ReferralPageClient />);

    expect(mocks.endCampaignIfExpired).not.toHaveBeenCalled();

    await act(async () => {
      vi.advanceTimersByTime(2_000);
    });

    expect(mocks.endCampaignIfExpired).toHaveBeenCalledTimes(1);
    expect(mocks.endCampaignIfExpired).toHaveBeenCalledWith(
      '00000000-0000-4000-8000-000000013500',
      expect.any(Object),
    );
  });
});

function referralResponse({ currentTenant }: { currentTenant: ReturnType<typeof leaderboardRow> }) {
  return {
    active: true,
    campaignId: '00000000-0000-4000-8000-000000013500',
    campaignName: 'Sự kiện referral FPT',
    campaignDescription: 'Mời bạn bè cùng dùng Zero Mail.',
    campaignStartsAt: '2026-06-06T00:00:00Z',
    campaignEndsAt: '2026-09-03T23:59:00Z',
    webBannerEnabled: true,
    countdownEnabled: true,
    leaderboardEnabled: true,
    bannerImageAvailable: true,
    code: 'DTH12345',
    url: 'https://zeromail.vn/referral?code=DTH12345',
    successfulReferrals: currentTenant.successfulReferrals,
    totalRankedTenants: 7,
    currentTenant,
    leaderboard: [
      leaderboardRow({ rank: 1, successfulReferrals: 31, tenantDisplayName: 'FPT Software' }),
      currentTenant.rank === 2
        ? currentTenant
        : leaderboardRow({ rank: 2, successfulReferrals: 29, tenantDisplayName: 'VNPT Solutions' }),
      leaderboardRow({ rank: 3, successfulReferrals: 25, tenantDisplayName: 'FPT IS' }),
    ],
    snapshotAt: '2026-09-04T00:00:00Z',
  };
}

function leaderboardRow({
  rank,
  successfulReferrals,
  currentTenant = false,
  tenantDisplayName = currentTenant ? 'DTH Software' : `Tenant ${rank}`,
}: {
  rank: number;
  successfulReferrals: number;
  currentTenant?: boolean;
  tenantDisplayName?: string;
}) {
  return {
    tenantId: `00000000-0000-4000-8000-00000001350${rank}`,
    tenantDisplayName,
    successfulReferrals,
    rank,
    currentTenant,
  };
}
