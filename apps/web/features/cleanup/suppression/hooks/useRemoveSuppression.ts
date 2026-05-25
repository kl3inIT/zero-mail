'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import {
  removeSuppression,
  type SuppressionEntryResponse,
} from '@/features/cleanup/suppression/api/suppression-api';
import { suppressionKeys } from '@/features/cleanup/suppression/query-keys';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

type RemoveVariables = { id: string };

type MutationContext = {
  previousEntries: SuppressionEntryResponse[] | undefined;
};

export function useRemoveSuppression() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<void, Error, RemoveVariables, MutationContext>({
    mutationFn: ({ id }) => removeSuppression(id),
    onMutate: async ({ id }) => {
      await queryClient.cancelQueries({ queryKey: suppressionKeys.list() });
      const previousEntries = queryClient.getQueryData<SuppressionEntryResponse[]>(
        suppressionKeys.list(),
      );
      queryClient.setQueryData<SuppressionEntryResponse[]>(
        suppressionKeys.list(),
        (currentEntries) => (currentEntries ?? []).filter((entry) => entry.id !== id),
      );
      return { previousEntries };
    },
    onSuccess: () => {
      toast.success(t('cleanup.suppression.removeOk'));
    },
    onError: (_mutationError, _variables, context) => {
      queryClient.setQueryData(suppressionKeys.list(), context?.previousEntries);
      toast.error(t('cleanup.suppression.err.generic'));
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: suppressionKeys.list() });
      await queryClient.invalidateQueries({ queryKey: unsubscribeCampaignKeys.all });
    },
  });
}
