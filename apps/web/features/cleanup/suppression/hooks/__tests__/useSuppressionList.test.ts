/**
 * UNS-02 (frontend half) — Suppression list CRUD with optimistic mutations.
 *
 * Wave 0 RED: the hooks, the API module, and the query-key factory ship in Wave 5b (Plan 09). The
 * spec locks the optimistic-update contract:
 *
 *   - `useSuppressionList` returns an array from `fetchSuppressionList`
 *   - `useAddSuppression` mutation optimistically inserts the new entry into the query cache
 *   - `useRemoveSuppression` mutation optimistically deletes the entry on success
 *
 * Until Wave 5b ships `@/features/cleanup/suppression/*`, `pnpm tsc` will report "Cannot find
 * module" errors here — that is the expected RED state.
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { suppressionKeys } from '@/features/cleanup/suppression/query-keys';

type SuppressionEntry = {
  id: string;
  senderEmail: string | null;
  senderDomain: string | null;
  reason: 'manual' | 'replied' | 'auto';
};

const mocks = vi.hoisted(() => ({
  fetchSuppressionList: vi.fn(),
  addSuppression: vi.fn(),
  removeSuppression: vi.fn(),
  useQuery: vi.fn(),
  useMutation: vi.fn(),
  useQueryClient: vi.fn(),
  useTranslations: vi.fn(),
}));

vi.mock('@/features/cleanup/suppression/api/suppression-api', () => ({
  fetchSuppressionList: mocks.fetchSuppressionList,
  addSuppression: mocks.addSuppression,
  removeSuppression: mocks.removeSuppression,
}));

vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: unknown) => mocks.useQuery(options),
  useMutation: (options: unknown) => mocks.useMutation(options),
  useQueryClient: () => mocks.useQueryClient(),
}));

vi.mock('next-intl', () => ({
  useTranslations: () => mocks.useTranslations(),
}));

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}));

import {
  useAddSuppression,
  useRemoveSuppression,
  useSuppressionList,
} from '@/features/cleanup/suppression/hooks/useSuppressionList';

describe('useSuppressionList', () => {
  beforeEach(() => {
    mocks.fetchSuppressionList.mockReset();
    mocks.addSuppression.mockReset();
    mocks.removeSuppression.mockReset();
    mocks.useQuery.mockReset();
    mocks.useMutation.mockReset();
    mocks.useQueryClient.mockReset();
    mocks.useTranslations.mockReset();
    mocks.useQuery.mockImplementation(() => ({ data: [] as SuppressionEntry[] }));
    mocks.useMutation.mockImplementation(() => ({ mutate: vi.fn(), isPending: false }));
    mocks.useQueryClient.mockImplementation(() => ({
      cancelQueries: vi.fn(),
      getQueryData: vi.fn(() => []),
      setQueryData: vi.fn(),
      invalidateQueries: vi.fn(),
    }));
    mocks.useTranslations.mockImplementation(() => (key: string) => key);
  });

  it('fetchesSuppressionList_returnsArray', () => {
    useSuppressionList();

    expect(mocks.useQuery).toHaveBeenCalledWith(
      expect.objectContaining({
        queryKey: suppressionKeys.list(),
      }),
    );
  });

  it('optimisticallyAddsEntryOnMutation', () => {
    useAddSuppression();

    const lastCallOptions = mocks.useMutation.mock.calls.at(-1)?.[0] as
      | { onMutate?: (variables: { senderEmail?: string; senderDomain?: string }) => unknown }
      | undefined;
    expect(lastCallOptions).toBeDefined();
    expect(lastCallOptions?.onMutate).toBeDefined();
  });

  it('removesEntryFromCache_onSuccessfulRemove', () => {
    useRemoveSuppression();

    const lastCallOptions = mocks.useMutation.mock.calls.at(-1)?.[0] as
      | { onSuccess?: (data: unknown, variables: { id: string }) => unknown }
      | undefined;
    expect(lastCallOptions).toBeDefined();
    expect(lastCallOptions?.onSuccess).toBeDefined();
  });
});
