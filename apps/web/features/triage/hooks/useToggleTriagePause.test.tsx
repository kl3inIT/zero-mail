import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { accountQueryKeys } from '@/features/account/query-keys';
import { billingKeys } from '@/features/billing/query-keys';
import { triageKeys } from '@/features/triage/query-keys';

type MutationContext = { previousPauseState: boolean | undefined } | undefined;
type MutationOptions = {
  mutationFn: (paused: boolean) => Promise<unknown>;
  onMutate?: (paused: boolean) => Promise<MutationContext>;
  onError?: (error: unknown, paused: boolean, context: MutationContext) => void;
  onSettled?: (
    data: unknown,
    error: unknown,
    paused: boolean,
    context: MutationContext,
  ) => Promise<void> | void;
};

const mocks = vi.hoisted(() => ({
  cancelQueries: vi.fn(),
  getQueryData: vi.fn(),
  invalidateQueries: vi.fn(),
  setQueryData: vi.fn(),
  setTriagePaused: vi.fn(),
  useMutation: vi.fn(),
}));

vi.mock('@/features/triage/api/triage-api', () => ({
  setTriagePaused: mocks.setTriagePaused,
}));

vi.mock('@tanstack/react-query', () => ({
  useMutation: (options: MutationOptions) => mocks.useMutation(options),
  useQueryClient: () => ({
    cancelQueries: mocks.cancelQueries,
    getQueryData: mocks.getQueryData,
    invalidateQueries: mocks.invalidateQueries,
    setQueryData: mocks.setQueryData,
  }),
}));

import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';

describe('useToggleTriagePause', () => {
  beforeEach(() => {
    mocks.cancelQueries.mockReset();
    mocks.getQueryData.mockReset();
    mocks.getQueryData.mockReturnValue(false);
    mocks.invalidateQueries.mockReset();
    mocks.setQueryData.mockReset();
    mocks.setTriagePaused.mockReset();
    mocks.setTriagePaused.mockResolvedValue(undefined);
    mocks.useMutation.mockReset();
    mocks.useMutation.mockImplementation((options: MutationOptions) => ({
      isPending: false,
      mutate: async (paused: boolean) => {
        const context = await options.onMutate?.(paused);
        try {
          const data = await options.mutationFn(paused);
          await options.onSettled?.(data, null, paused, context);
          return data;
        } catch (error) {
          options.onError?.(error, paused, context);
          await options.onSettled?.(undefined, error, paused, context);
          throw error;
        }
      },
    }));
  });

  it('calls the pause API with the requested state', async () => {
    const { result } = renderHook(() => useToggleTriagePause());

    await act(async () => {
      await result.current.mutate(true);
    });

    expect(mocks.setTriagePaused).toHaveBeenCalledWith(true);
  });

  it('optimistically writes the single triage pause cache entry', async () => {
    const { result } = renderHook(() => useToggleTriagePause());

    await act(async () => {
      await result.current.mutate(true);
    });

    expect(mocks.cancelQueries).toHaveBeenCalledWith({ queryKey: triageKeys.pauseState() });
    expect(mocks.getQueryData).toHaveBeenCalledWith(triageKeys.pauseState());
    expect(mocks.setQueryData).toHaveBeenCalledWith(triageKeys.pauseState(), true);
    expect(mocks.setQueryData.mock.calls.map(([queryKey]) => queryKey)).toEqual([
      triageKeys.pauseState(),
    ]);
  });

  it('rolls back the triage pause cache entry on error', async () => {
    const apiError = new Error('pause failed');
    mocks.getQueryData.mockReturnValue(false);
    mocks.setTriagePaused.mockRejectedValue(apiError);
    const { result } = renderHook(() => useToggleTriagePause());

    await expect(
      act(async () => {
        await result.current.mutate(true);
      }),
    ).rejects.toThrow(apiError);

    expect(mocks.setQueryData).toHaveBeenCalledWith(triageKeys.pauseState(), false);
  });

  it('invalidates pause state, billing balance, and current user on settle', async () => {
    const { result } = renderHook(() => useToggleTriagePause());

    await act(async () => {
      await result.current.mutate(false);
    });

    await waitFor(() => {
      expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: triageKeys.pauseState() });
      expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: billingKeys.balance() });
      expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: accountQueryKeys.me() });
    });
  });
});
