/**
 * UNS-05 (frontend half) — `useCampaignStatus(jobId)` polling contract.
 *
 * Wave 0 RED: the hook, the API module, and the query-key factory are all introduced in Wave 5b
 * (Plan 09). This Vitest spec mocks the future modules and locks the polling behavior:
 *
 *   - `refetchInterval` = 2000 ms while `status ∈ {QUEUED, RUNNING}`
 *   - `refetchInterval` = `false` once `status` is terminal (`COMPLETED` | `FAILED`)
 *   - Query key uses `unsubscribeCampaignKeys.byId(jobId)`
 *
 * Until Wave 5b ships the modules at `@/features/cleanup/unsubscribe-campaign/*`, `pnpm tsc` will
 * report "Cannot find module" errors here — that is the expected RED state.
 */

import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

type UseQueryOptionsForTest = {
  queryKey: readonly unknown[];
  queryFn: (context: { signal?: AbortSignal }) => Promise<unknown>;
  refetchInterval?: number | ((query: { state: { data?: { status?: string } } }) => number | false);
  refetchIntervalInBackground?: boolean;
};

const mocks = vi.hoisted(() => ({
  fetchCampaignStatus: vi.fn(),
  useQuery: vi.fn(),
}));

vi.mock('@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api', () => ({
  fetchCampaignStatus: mocks.fetchCampaignStatus,
}));

vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: UseQueryOptionsForTest) => mocks.useQuery(options),
}));

import { useCampaignStatus } from '@/features/cleanup/unsubscribe-campaign/hooks/useCampaignStatus';

describe('useCampaignStatus', () => {
  const jobId = '00000000-0000-0000-0000-000000000001';

  beforeEach(() => {
    vi.useRealTimers();
    mocks.fetchCampaignStatus.mockReset();
    mocks.useQuery.mockReset();
    mocks.useQuery.mockImplementation(() => ({ data: undefined }));
  });

  it('usesCorrectQueryKey', () => {
    renderHook(() => useCampaignStatus(jobId));

    expect(mocks.useQuery).toHaveBeenCalledWith(
      expect.objectContaining({
        queryKey: unsubscribeCampaignKeys.byId(jobId),
      }),
    );
  });

  it('pollsEvery2sWhenStatusIsQueued', () => {
    renderHook(() => useCampaignStatus(jobId));

    const lastCall = mocks.useQuery.mock.calls.at(-1)?.[0] as UseQueryOptionsForTest | undefined;
    expect(lastCall).toBeDefined();
    if (lastCall && typeof lastCall.refetchInterval === 'function') {
      const queuedInterval = lastCall.refetchInterval({
        state: { data: { status: 'QUEUED' } },
      });
      expect(queuedInterval).toBe(2000);

      const runningInterval = lastCall.refetchInterval({
        state: { data: { status: 'RUNNING' } },
      });
      expect(runningInterval).toBe(2000);
    }
  });

  it('stopsPollingWhenStatusBecomesCompleted', () => {
    renderHook(() => useCampaignStatus(jobId));

    const lastCall = mocks.useQuery.mock.calls.at(-1)?.[0] as UseQueryOptionsForTest | undefined;
    expect(lastCall).toBeDefined();
    if (lastCall && typeof lastCall.refetchInterval === 'function') {
      const refetchValue = lastCall.refetchInterval({ state: { data: { status: 'COMPLETED' } } });
      expect(refetchValue).toBe(false);
    }
  });

  it('stopsPollingWhenStatusBecomesFailed', () => {
    renderHook(() => useCampaignStatus(jobId));

    const lastCall = mocks.useQuery.mock.calls.at(-1)?.[0] as UseQueryOptionsForTest | undefined;
    expect(lastCall).toBeDefined();
    if (lastCall && typeof lastCall.refetchInterval === 'function') {
      const refetchValue = lastCall.refetchInterval({ state: { data: { status: 'FAILED' } } });
      expect(refetchValue).toBe(false);
    }
  });
});
