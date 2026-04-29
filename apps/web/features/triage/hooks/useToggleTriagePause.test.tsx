import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { accountKeys } from '@/features/account/api/keys';

const mocks = vi.hoisted(() => ({
  invalidateQueries: vi.fn(),
  setTriagePaused: vi.fn(),
}));

vi.mock('@/features/triage/api/triagePause', () => ({
  setTriagePaused: mocks.setTriagePaused,
  toggleTriagePause: mocks.setTriagePaused,
}));

vi.mock('@tanstack/react-query', () => ({
  useMutation: (options: {
    mutationFn: (paused: boolean) => Promise<unknown>;
    onSuccess?: () => void;
  }) => ({
    mutate: async (paused: boolean) => {
      await options.mutationFn(paused);
      options.onSuccess?.();
    },
  }),
  useQueryClient: () => ({
    invalidateQueries: mocks.invalidateQueries,
  }),
}));

import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';

describe('useToggleTriagePause', () => {
  beforeEach(() => {
    mocks.invalidateQueries.mockReset();
    mocks.setTriagePaused.mockReset();
    mocks.setTriagePaused.mockResolvedValue({ paused: true });
  });

  it('mutate_callsSetTriagePaused', async () => {
    const { result } = renderHook(() => useToggleTriagePause());

    await act(async () => {
      await result.current.mutate(true);
    });

    expect(mocks.setTriagePaused).toHaveBeenCalledWith(true);
  });

  it('onSuccess_invalidates_me_key', async () => {
    const { result } = renderHook(() => useToggleTriagePause());

    await act(async () => {
      await result.current.mutate(false);
    });

    await waitFor(() => {
      expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: accountKeys.me() });
    });
  });
});
