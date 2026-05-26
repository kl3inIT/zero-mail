'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { deleteKnowledgeSnippet } from '@/features/knowledge/api/knowledge-api';
import { knowledgeKeys } from '@/features/knowledge/query-keys';

export function useDeleteKnowledge() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<void, Error, string>({
    mutationFn: deleteKnowledgeSnippet,
    meta: {
      successMessage: t('ai.toast.snippetDeleted'),
      errorMessage: t('ai.toast.genericFailure'),
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: knowledgeKeys.list() });
    },
  });
}
