'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { endReferralCampaignIfExpired } from '@/features/referrals/api/referrals-api';
import { referralKeys } from '@/features/referrals/query-keys';

export function useEndReferralCampaignIfExpired() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (campaignId: string) => endReferralCampaignIfExpired(campaignId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: referralKeys.me() });
    },
    meta: { silent: true },
  });
}
