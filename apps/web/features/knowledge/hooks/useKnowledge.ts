'use client';

import { useQuery } from '@tanstack/react-query';

import { listKnowledgeSnippets } from '@/features/knowledge/api/knowledge-api';
import { knowledgeKeys } from '@/features/knowledge/query-keys';

export function useKnowledge() {
  return useQuery({ queryKey: knowledgeKeys.list(), queryFn: listKnowledgeSnippets });
}
