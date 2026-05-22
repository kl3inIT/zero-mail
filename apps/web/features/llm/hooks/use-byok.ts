'use client';

import { useMutation, useQuery } from '@tanstack/react-query';

import { getCurrentByok, saveByok, validateByok } from '@/features/llm/api/llm-api';
import { byokKeys } from '@/features/llm/query-keys';

export function useCurrentByok() {
  return useQuery({ queryKey: byokKeys.current(), queryFn: getCurrentByok });
}

export function useValidateByok() {
  return useMutation({ mutationFn: validateByok });
}

export function useSaveByok() {
  return useMutation({ mutationFn: saveByok });
}
