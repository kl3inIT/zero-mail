'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import {
  updateKnowledgeSnippet,
  type KnowledgeSnippet,
  type KnowledgeSnippetRequest,
} from '@/features/knowledge/api/knowledge-api';
import { knowledgeKeys } from '@/features/knowledge/query-keys';

export function useUpdateKnowledge() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<KnowledgeSnippet, Error, { id: string; body: KnowledgeSnippetRequest }>({
    mutationFn: updateKnowledgeSnippet,
    meta: {
      successMessage: t('ai.toast.snippetUpdated'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: knowledgeKeys.list() });
    },
  });
}
