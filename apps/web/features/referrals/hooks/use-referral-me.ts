'use client';

import { useQuery } from '@tanstack/react-query';

import { fetchReferralMe } from '@/features/referrals/api/referrals-api';
import { referralKeys } from '@/features/referrals/query-keys';

export const REFERRAL_ME_REFETCH_INTERVAL_MS = 15_000;

export function useReferralMe() {
  return useQuery({
    queryKey: referralKeys.me(),
    queryFn: ({ signal }) => fetchReferralMe({ signal }),
    refetchInterval: REFERRAL_ME_REFETCH_INTERVAL_MS,
    refetchIntervalInBackground: false,
    refetchOnMount: 'always',
    refetchOnWindowFocus: true,
    staleTime: 0,
    meta: {
      errorMessage: 'Không thể tải chương trình giới thiệu.',
    },
  });
}
