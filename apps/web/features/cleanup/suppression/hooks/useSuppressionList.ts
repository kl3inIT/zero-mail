'use client';

import { useQuery } from '@tanstack/react-query';

import { fetchSuppressionList } from '@/features/cleanup/suppression/api/suppression-api';
import { suppressionKeys } from '@/features/cleanup/suppression/query-keys';

export { useAddSuppression } from '@/features/cleanup/suppression/hooks/useAddSuppression';
export { useRemoveSuppression } from '@/features/cleanup/suppression/hooks/useRemoveSuppression';

export function useSuppressionList() {
  return useQuery({
    queryKey: suppressionKeys.list(),
    queryFn: fetchSuppressionList,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}
