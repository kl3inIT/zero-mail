'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import {
  createKnowledgeSnippet,
  type KnowledgeSnippet,
  type KnowledgeSnippetRequest,
} from '@/features/knowledge/api/knowledge-api';
import { knowledgeKeys } from '@/features/knowledge/query-keys';

export function useCreateKnowledge() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<KnowledgeSnippet, Error, KnowledgeSnippetRequest>({
    mutationFn: createKnowledgeSnippet,
    meta: {
      successMessage: t('ai.toast.snippetAdded'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: knowledgeKeys.list() });
    },
  });
}
