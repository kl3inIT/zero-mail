'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import {
  executeCampaign,
  type CampaignExecuteRequest,
  type CampaignExecuteResponse,
} from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

function extractStatusAndCode(error: unknown): { status?: number; errorCode?: string } {
  if (error && typeof error === 'object') {
    const errorObject = error as { status?: unknown; code?: unknown; errorCode?: unknown };
    const status = typeof errorObject.status === 'number' ? errorObject.status : undefined;
    const errorCode =
      typeof errorObject.errorCode === 'string'
        ? errorObject.errorCode
        : typeof errorObject.code === 'string'
          ? errorObject.code
          : undefined;
    return { status, errorCode };
  }
  return {};
}

export function useExecuteCampaign(window: string = '30d') {
  const queryClient = useQueryClient();
  const router = useRouter();
  const t = useTranslations();

  return useMutation<CampaignExecuteResponse, Error, CampaignExecuteRequest>({
    mutationFn: executeCampaign,
    onSuccess: (executeResponse) => {
      toast.success(t('cleanup.unsubscribe.preview.submitOk'));
      void queryClient.invalidateQueries({
        queryKey: unsubscribeCampaignKeys.candidates(window),
      });
      if (executeResponse.jobId) {
        router.push(`/cleanup/unsubscribe-campaign/${executeResponse.jobId}`);
      }
    },
    onError: (mutationError) => {
      const { status, errorCode } = extractStatusAndCode(mutationError);
      if (status === 400 && errorCode === 'error.cleanup.campaign.too_many_senders') {
        toast.error(t('cleanup.unsubscribe.preview.errCapSender'));
      } else if (status === 400 && errorCode === 'error.cleanup.campaign.too_many_messages') {
        toast.error(t('cleanup.unsubscribe.preview.errCapMessage'));
      } else {
        toast.error(t('cleanup.unsubscribe.preview.errGeneric'));
      }
    },
  });
}
