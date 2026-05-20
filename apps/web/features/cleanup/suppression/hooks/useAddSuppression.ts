'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import {
  addSuppression,
  type SuppressionAddRequest,
  type SuppressionEntryResponse,
} from '@/features/cleanup/suppression/api/suppression-api';
import { suppressionKeys } from '@/features/cleanup/suppression/query-keys';

type MutationContext = {
  previousEntries: SuppressionEntryResponse[] | undefined;
};

function extractStatusCode(error: unknown): number | undefined {
  if (error && typeof error === 'object' && 'status' in error) {
    const status = (error as { status?: unknown }).status;
    return typeof status === 'number' ? status : undefined;
  }
  return undefined;
}

export function useAddSuppression() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<SuppressionEntryResponse, Error, SuppressionAddRequest, MutationContext>({
    mutationFn: addSuppression,
    onMutate: async (newEntry) => {
      await queryClient.cancelQueries({ queryKey: suppressionKeys.list() });
      const previousEntries = queryClient.getQueryData<SuppressionEntryResponse[]>(
        suppressionKeys.list(),
      );
      const optimisticEntry: SuppressionEntryResponse = {
        id: `optimistic-${Date.now()}`,
        senderEmail: newEntry.senderEmailOrDomain.includes('@')
          ? newEntry.senderEmailOrDomain
          : undefined,
        senderDomain: newEntry.senderEmailOrDomain.includes('@')
          ? undefined
          : newEntry.senderEmailOrDomain,
        reason: 'manual',
        createdAt: new Date().toISOString(),
      };
      queryClient.setQueryData<SuppressionEntryResponse[]>(
        suppressionKeys.list(),
        (currentEntries) => [...(currentEntries ?? []), optimisticEntry],
      );
      return { previousEntries };
    },
    onSuccess: () => {
      toast.success(t('cleanup.suppression.addOk'));
    },
    onError: (mutationError, _variables, context) => {
      queryClient.setQueryData(suppressionKeys.list(), context?.previousEntries);
      const status = extractStatusCode(mutationError);
      if (status === 409) {
        toast.error(t('cleanup.suppression.err.duplicate'));
      } else if (status === 400) {
        toast.error(t('cleanup.suppression.err.invalid'));
      } else {
        toast.error(t('cleanup.suppression.err.generic'));
      }
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: suppressionKeys.list() });
    },
  });
}
