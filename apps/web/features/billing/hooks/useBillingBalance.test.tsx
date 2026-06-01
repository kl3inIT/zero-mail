import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { billingKeys } from '@/features/billing/query-keys';

type UseQueryOptionsForTest = {
  queryKey: readonly unknown[];
  queryFn: (context: { signal?: AbortSignal }) => Promise<unknown>;
  refetchInterval?: number;
  refetchIntervalInBackground?: boolean;
  staleTime?: number;
};

const mocks = vi.hoisted(() => ({
  getBillingBalance: vi.fn(),
  useQuery: vi.fn(),
}));

vi.mock('@/features/billing/api/billing-api', () => ({
  getBillingBalance: mocks.getBillingBalance,
}));

vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: UseQueryOptionsForTest) => mocks.useQuery(options),
}));

import {
  BILLING_BALANCE_REFETCH_INTERVAL_MS,
  BILLING_BALANCE_STALE_TIME_MS,
  useBillingBalance,
} from '@/features/billing/hooks/useBillingBalance';

describe('useBillingBalance', () => {
  beforeEach(() => {
    vi.useRealTimers();
    mocks.getBillingBalance.mockReset();
    mocks.getBillingBalance.mockResolvedValue({
      availableCredits: 12,
      heldCredits: 0,
      currency: 'credits',
      monthlyCredits: 300,
      additionalCredits: 0,
      monthlyCreditAllowance: 300,
      resetsAt: '2026-06-01T00:00:00.000Z',
    });
    mocks.useQuery.mockReset();
    mocks.useQuery.mockImplementation(() => ({ data: undefined }));
  });

  it('configures a 45s poll with a 30s stale window', () => {
    renderHook(() => useBillingBalance());

    expect(mocks.useQuery).toHaveBeenCalledWith(
      expect.objectContaining({
        queryKey: billingKeys.balance(),
        refetchInterval: BILLING_BALANCE_REFETCH_INTERVAL_MS,
        refetchIntervalInBackground: false,
        staleTime: BILLING_BALANCE_STALE_TIME_MS,
      }),
    );
  });

  it('refetches after 45 seconds despite the global 5 minute stale time', async () => {
    vi.useFakeTimers();
    mocks.useQuery.mockImplementation((options: UseQueryOptionsForTest) => {
      void options.queryFn({ signal: undefined });
      if (typeof options.refetchInterval === 'number') {
        setInterval(() => void options.queryFn({ signal: undefined }), options.refetchInterval);
      }
      return { data: undefined };
    });

    renderHook(() => useBillingBalance());

    expect(mocks.getBillingBalance).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(BILLING_BALANCE_REFETCH_INTERVAL_MS);
    expect(mocks.getBillingBalance).toHaveBeenCalledTimes(2);

    vi.useRealTimers();
  });
});
