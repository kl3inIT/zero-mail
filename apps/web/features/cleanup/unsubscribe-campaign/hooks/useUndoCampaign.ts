'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import { undoCampaign } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

type UndoVariables = { jobId: string; restoredCount?: number };

function extractStatusCode(error: unknown): number | undefined {
  if (error && typeof error === 'object' && 'status' in error) {
    const status = (error as { status?: unknown }).status;
    return typeof status === 'number' ? status : undefined;
  }
  return undefined;
}

export function useUndoCampaign() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<void, Error, UndoVariables>({
    mutationFn: ({ jobId }) => undoCampaign(jobId),
    onSuccess: (_data, variables) => {
      toast.success(t('cleanup.unsubscribe.undo.ok', { count: variables.restoredCount ?? 0 }));
      void queryClient.invalidateQueries({
        queryKey: unsubscribeCampaignKeys.byId(variables.jobId),
      });
    },
    onError: (mutationError, variables) => {
      const status = extractStatusCode(mutationError);
      if (status === 410) {
        toast.error(t('cleanup.unsubscribe.undo.windowExpiredToast'));
      } else {
        toast.error(t('cleanup.unsubscribe.undo.generic'));
      }
      void queryClient.invalidateQueries({
        queryKey: unsubscribeCampaignKeys.byId(variables.jobId),
      });
    },
  });
}
