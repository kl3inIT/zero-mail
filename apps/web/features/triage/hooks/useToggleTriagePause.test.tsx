import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { CurrentUser } from '@/features/account/api/account-api';
import { accountQueryKeys } from '@/features/account/query-keys';
import { billingKeys } from '@/features/billing/query-keys';

type MutationContext = { previousUser: CurrentUser | undefined } | undefined;
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

const baseUser: CurrentUser = {
  id: 'user-1',
  email: 'user@example.com',
  tenantId: 'tenant-1',
  preferredLanguage: 'en',
  onboardingStep: 'COMPLETE',
  triagePaused: false,
} as unknown as CurrentUser;

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
    mocks.getQueryData.mockReturnValue({ ...baseUser });
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

  it('optimistically writes triagePaused on the shared /me cache entry', async () => {
    const { result } = renderHook(() => useToggleTriagePause());

    await act(async () => {
      await result.current.mutate(true);
    });

    expect(mocks.cancelQueries).toHaveBeenCalledWith({ queryKey: accountQueryKeys.me() });
    expect(mocks.getQueryData).toHaveBeenCalledWith(accountQueryKeys.me());
    expect(mocks.setQueryData).toHaveBeenCalledWith(accountQueryKeys.me(), {
      ...baseUser,
      triagePaused: true,
    });
  });

  it('rolls back the /me cache entry on error', async () => {
    const apiError = new Error('pause failed');
    const previousUser = { ...baseUser, triagePaused: false };
    mocks.getQueryData.mockReturnValue(previousUser);
    mocks.setTriagePaused.mockRejectedValue(apiError);
    const { result } = renderHook(() => useToggleTriagePause());

    await expect(
      act(async () => {
        await result.current.mutate(true);
      }),
    ).rejects.toThrow(apiError);

    expect(mocks.setQueryData).toHaveBeenCalledWith(accountQueryKeys.me(), previousUser);
  });

  it('invalidates /me and billing balance on settle', async () => {
    const { result } = renderHook(() => useToggleTriagePause());

    await act(async () => {
      await result.current.mutate(false);
    });

    await waitFor(() => {
      expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: accountQueryKeys.me() });
      expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: billingKeys.balance() });
    });
  });
});
