'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import { retrySender } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

type RetryVariables = { jobId: string; senderEmail: string };

function extractStatusCode(error: unknown): number | undefined {
  if (error && typeof error === 'object' && 'status' in error) {
    const status = (error as { status?: unknown }).status;
    return typeof status === 'number' ? status : undefined;
  }
  return undefined;
}

function maskLocalPart(senderEmail: string): string {
  const atIndex = senderEmail.indexOf('@');
  if (atIndex < 0) return '***';
  return `***${senderEmail.slice(atIndex)}`;
}

export function useRetrySender() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<void, Error, RetryVariables>({
    mutationFn: ({ jobId, senderEmail }) => retrySender(jobId, senderEmail),
    onSuccess: (_data, variables) => {
      toast.success(
        t('cleanup.unsubscribe.status.retryOk', {
          sender: maskLocalPart(variables.senderEmail),
        }),
      );
      void queryClient.invalidateQueries({
        queryKey: unsubscribeCampaignKeys.byId(variables.jobId),
      });
    },
    onError: (mutationError, variables) => {
      const status = extractStatusCode(mutationError);
      if (status === 409) {
        toast.info(t('cleanup.unsubscribe.retry.alreadyOk'));
      } else {
        toast.error(t('cleanup.unsubscribe.retry.generic'));
      }
      void queryClient.invalidateQueries({
        queryKey: unsubscribeCampaignKeys.byId(variables.jobId),
      });
    },
  });
}
