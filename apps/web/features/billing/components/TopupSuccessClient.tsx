'use client';

import { LoadingState } from '@/components/states/LoadingState';
import { TopupSuccess } from '@/features/billing/components/TopupSuccess';
import { useBillingBalance } from '@/features/billing/hooks/useBillingBalance';

export function TopupSuccessClient() {
  const balance = useBillingBalance();

  if (balance.isLoading) {
    return <LoadingState variant="cards" count={1} />;
  }

  return <TopupSuccess newBalance={balance.data?.availableCredits ?? 0} />;
}
