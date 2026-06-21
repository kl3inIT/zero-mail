export const referralQueryKeys = {
  all: ['referrals'] as const,
  campaigns: () => [...referralQueryKeys.all, 'campaigns'] as const,
  dashboard: (campaignId: string, from: string, to: string) =>
    [...referralQueryKeys.all, 'dashboard', campaignId, from, to] as const,
};
