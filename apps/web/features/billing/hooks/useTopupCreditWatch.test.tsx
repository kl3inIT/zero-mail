import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

type BalanceQueryData = {
  availableCredits?: number;
};

type RefetchIntervalCallback = (query: { state: { data?: BalanceQueryData } }) => number | false;

type UseQueryOptionsForTest = {
  refetchInterval?: RefetchIntervalCallback;
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

import { BILLING_BALANCE_REFETCH_INTERVAL_MS } from '@/features/billing/hooks/useBillingBalance';
import { useTopupCreditWatch } from '@/features/billing/hooks/useTopupCreditWatch';

describe('useTopupCreditWatch', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-05-12T00:00:00Z'));
    mocks.getBillingBalance.mockReset();
    mocks.useQuery.mockReset();
    mocks.useQuery.mockImplementation(() => ({ data: { availableCredits: 10 } }));
  });

  it('continues polling while the intent is active and the balance has not risen', () => {
    renderHook(() =>
      useTopupCreditWatch({
        baselineCredits: 10,
        expiresAt: '2026-05-12T00:05:00Z',
      }),
    );

    const options = mocks.useQuery.mock.calls[0]?.[0] as UseQueryOptionsForTest;

    expect(options.refetchInterval?.({ state: { data: { availableCredits: 10 } } })).toBe(
      BILLING_BALANCE_REFETCH_INTERVAL_MS,
    );
  });

  it('stops polling once the balance rises above the baseline', () => {
    renderHook(() =>
      useTopupCreditWatch({
        baselineCredits: 10,
        expiresAt: '2026-05-12T00:05:00Z',
      }),
    );

    const options = mocks.useQuery.mock.calls[0]?.[0] as UseQueryOptionsForTest;

    expect(options.refetchInterval?.({ state: { data: { availableCredits: 11 } } })).toBe(false);
  });

  it('stops polling once the top-up intent expires', () => {
    renderHook(() =>
      useTopupCreditWatch({
        baselineCredits: 10,
        expiresAt: '2026-05-11T23:59:59Z',
      }),
    );

    const options = mocks.useQuery.mock.calls[0]?.[0] as UseQueryOptionsForTest;

    expect(options.refetchInterval?.({ state: { data: { availableCredits: 10 } } })).toBe(false);
  });
});
