'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  ComposerDraftApiError,
  deleteComposerDraft,
  getComposerDraft,
  upsertComposerDraft,
  type ComposerDraftSnapshot,
  type ComposerDraftUpsertInput,
} from '@/features/inbox/api/composer-draft-api';
import { inboxKeys } from '@/features/inbox/query-keys';

export function useComposerDraft(gmailThreadId: string | null) {
  return useQuery<ComposerDraftSnapshot | null, ComposerDraftApiError>({
    queryKey: inboxKeys.composerDraft(gmailThreadId),
    queryFn: () => {
      if (!gmailThreadId) return Promise.resolve(null);
      return getComposerDraft(gmailThreadId);
    },
    enabled: Boolean(gmailThreadId),
    staleTime: 30_000,
    retry: false,
    meta: { silent: true },
  });
}

export function useUpsertComposerDraft() {
  const queryClient = useQueryClient();
  return useMutation<ComposerDraftSnapshot, ComposerDraftApiError, ComposerDraftUpsertInput>({
    mutationFn: (input) => upsertComposerDraft(input),
    onSuccess: (snapshot, variables) => {
      queryClient.setQueryData(inboxKeys.composerDraft(variables.gmailThreadId), snapshot);
    },
    meta: { silent: true },
  });
}

export function useDeleteComposerDraft() {
  const queryClient = useQueryClient();
  return useMutation<void, ComposerDraftApiError, { draftId: string; gmailThreadId: string }>({
    mutationFn: ({ draftId }) => deleteComposerDraft(draftId),
    onSuccess: (_void, variables) => {
      queryClient.setQueryData(inboxKeys.composerDraft(variables.gmailThreadId), null);
    },
    meta: { silent: true },
  });
}
