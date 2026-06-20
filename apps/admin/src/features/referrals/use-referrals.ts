import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  createReferralCampaign,
  fetchReferralCampaigns,
  fetchReferralDashboard,
  updateReferralCampaign,
  updateReferralCampaignStatus,
  uploadReferralCampaignBanner,
  type AdminReferralCampaignListResponse,
  type ReferralDashboardQueryInput,
} from './referrals-api';
import { referralQueryKeys } from './query-keys';

export function useReferralCampaigns() {
  return useQuery({
    queryKey: referralQueryKeys.campaigns(),
    queryFn: fetchReferralCampaigns,
    refetchOnWindowFocus: false,
    meta: {
      errorMessage: 'Không thể tải danh sách sự kiện referral.',
    },
  });
}

export function useReferralDashboard(input: ReferralDashboardQueryInput | undefined) {
  return useQuery({
    queryKey: input
      ? referralQueryKeys.dashboard(
          input.campaignId,
          input.from,
          input.to,
          input.leaderboardLimit,
        )
      : referralQueryKeys.dashboard('none', 'none', 'none', 0),
    queryFn: () => {
      if (!input) throw new Error('Missing referral dashboard query.');
      return fetchReferralDashboard(input);
    },
    enabled: Boolean(input),
    refetchOnWindowFocus: false,
    meta: {
      errorMessage: 'Không thể tải dashboard referral.',
    },
  });
}

export function useCreateReferralCampaign() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createReferralCampaign,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: referralQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã tạo sự kiện referral.',
      errorMessage: 'Không thể tạo sự kiện referral.',
    },
  });
}

export function useUpdateReferralCampaign() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateReferralCampaign,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: referralQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã lưu cấu hình sự kiện.',
      errorMessage: 'Không thể lưu cấu hình sự kiện.',
    },
  });
}

export function useUpdateReferralCampaignStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateReferralCampaignStatus,
    onMutate: async ({ campaignId, status }) => {
      await queryClient.cancelQueries({ queryKey: referralQueryKeys.campaigns() });
      const previousCampaigns = queryClient.getQueryData<AdminReferralCampaignListResponse>(
        referralQueryKeys.campaigns(),
      );

      queryClient.setQueryData<AdminReferralCampaignListResponse>(
        referralQueryKeys.campaigns(),
        (currentCampaigns) =>
          currentCampaigns
            ? {
                ...currentCampaigns,
                campaigns: currentCampaigns.campaigns.map((campaign) =>
                  campaign.campaignId === campaignId ? { ...campaign, status } : campaign,
                ),
              }
            : currentCampaigns,
      );

      return { previousCampaigns };
    },
    onError: (_error, _variables, context) => {
      if (context?.previousCampaigns) {
        queryClient.setQueryData(referralQueryKeys.campaigns(), context.previousCampaigns);
      }
    },
    onSuccess: (updatedCampaign) => {
      queryClient.setQueryData<AdminReferralCampaignListResponse>(
        referralQueryKeys.campaigns(),
        (currentCampaigns) =>
          currentCampaigns
            ? {
                ...currentCampaigns,
                campaigns: currentCampaigns.campaigns.map((campaign) =>
                  campaign.campaignId === updatedCampaign.campaignId ? updatedCampaign : campaign,
                ),
              }
            : currentCampaigns,
      );
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: referralQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã cập nhật trạng thái sự kiện.',
      errorMessage: 'Không thể cập nhật trạng thái sự kiện.',
    },
  });
}

export function useUploadReferralCampaignBanner() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: uploadReferralCampaignBanner,
    onSuccess: (updatedCampaign) => {
      queryClient.setQueryData<AdminReferralCampaignListResponse>(
        referralQueryKeys.campaigns(),
        (currentCampaigns) =>
          currentCampaigns
            ? {
                ...currentCampaigns,
                campaigns: currentCampaigns.campaigns.map((campaign) =>
                  campaign.campaignId === updatedCampaign.campaignId ? updatedCampaign : campaign,
                ),
              }
            : currentCampaigns,
      );
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: referralQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã tải banner sự kiện.',
      errorMessage: 'Không thể tải banner sự kiện.',
    },
  });
}
