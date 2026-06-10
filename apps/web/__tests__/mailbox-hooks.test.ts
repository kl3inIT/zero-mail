import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { analyticsKeys } from '@/features/analytics/query-keys';
import { inboxKeys } from '@/features/inbox/query-keys';
import { mailboxQueryKeys } from '@/features/mailbox/query-keys';
import { needsReplyKeys } from '@/features/needs-reply/query-keys';
import { rulesKeys } from '@/features/rules/query-keys';
import { triageKeys } from '@/features/triage/query-keys';

type UseQueryOptionsForTest = {
  queryKey: readonly unknown[];
  queryFn: (context: { signal?: AbortSignal }) => Promise<unknown>;
};

type UseMutationOptionsForTest = {
  mutationFn: (gmailConnectionId: string) => Promise<unknown>;
  onSuccess?: () => Promise<void> | void;
  meta?: {
    successMessage?: string;
    errorMessage?: string;
  };
};

const mocks = vi.hoisted(() => ({
  getActiveMailbox: vi.fn(),
  invalidateQueries: vi.fn(),
  listMailboxes: vi.fn(),
  mutationOptions: undefined as UseMutationOptionsForTest | undefined,
  setActiveMailbox: vi.fn(),
  useMutation: vi.fn(),
  useQuery: vi.fn(),
}));

vi.mock('@/features/mailbox/api/mailbox-api', () => ({
  getActiveMailbox: mocks.getActiveMailbox,
  listMailboxes: mocks.listMailboxes,
  setActiveMailbox: mocks.setActiveMailbox,
}));

vi.mock('@tanstack/react-query', () => ({
  useMutation: (options: UseMutationOptionsForTest) => mocks.useMutation(options),
  useQuery: (options: UseQueryOptionsForTest) => mocks.useQuery(options),
  useQueryClient: () => ({
    invalidateQueries: mocks.invalidateQueries,
  }),
}));

import { useActiveMailbox } from '@/features/mailbox/hooks/useActiveMailbox';
import { useMailboxList } from '@/features/mailbox/hooks/useMailboxList';
import { useSetActiveMailbox } from '@/features/mailbox/hooks/useSetActiveMailbox';

describe('mailbox hooks', () => {
  beforeEach(() => {
    mocks.getActiveMailbox.mockReset();
    mocks.getActiveMailbox.mockResolvedValue({ gmailConnectionId: 'mailbox-a' });
    mocks.invalidateQueries.mockReset();
    mocks.invalidateQueries.mockResolvedValue(undefined);
    mocks.listMailboxes.mockReset();
    mocks.listMailboxes.mockResolvedValue([{ gmailConnectionId: 'mailbox-a' }]);
    mocks.setActiveMailbox.mockReset();
    mocks.setActiveMailbox.mockResolvedValue({ gmailConnectionId: 'mailbox-b' });
    mocks.mutationOptions = undefined;
    mocks.useMutation.mockReset();
    mocks.useMutation.mockImplementation((options: UseMutationOptionsForTest) => {
      mocks.mutationOptions = options;
      return {
        mutateAsync: async (gmailConnectionId: string) => {
          const data = await options.mutationFn(gmailConnectionId);
          await options.onSuccess?.();
          return data;
        },
      };
    });
    mocks.useQuery.mockReset();
    mocks.useQuery.mockImplementation((options: UseQueryOptionsForTest) => {
      void options.queryFn({ signal: undefined });
      return { data: undefined };
    });
  });

  it('fetches the mailbox list with the mailbox list query key', () => {
    renderHook(() => useMailboxList());

    expect(mocks.useQuery).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: mailboxQueryKeys.list() }),
    );
    expect(mocks.listMailboxes).toHaveBeenCalledWith({ signal: undefined });
  });

  it('fetches the active mailbox with the active mailbox query key', () => {
    renderHook(() => useActiveMailbox());

    expect(mocks.useQuery).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: mailboxQueryKeys.active() }),
    );
    expect(mocks.getActiveMailbox).toHaveBeenCalledWith({ signal: undefined });
  });

  it('invalidates mailbox-scoped caches after switching the active mailbox', async () => {
    const { result } = renderHook(() => useSetActiveMailbox());

    await result.current.mutateAsync('mailbox-b');

    expect(mocks.setActiveMailbox).toHaveBeenCalledWith('mailbox-b');
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: mailboxQueryKeys.all });
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: inboxKeys.all });
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: needsReplyKeys.all });
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: rulesKeys.all });
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: triageKeys.all });
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: analyticsKeys.all });
    expect(mocks.mutationOptions?.meta).toEqual({
      successMessage: 'Active mailbox updated',
      errorMessage: 'Could not switch active mailbox',
    });
  });
});
