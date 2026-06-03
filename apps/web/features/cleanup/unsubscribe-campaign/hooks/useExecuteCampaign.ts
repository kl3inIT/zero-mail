'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
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

type ExecuteCampaignOptions = {
  onSuccess?: (executeResponse: CampaignExecuteResponse, variables: CampaignExecuteRequest) => void;
};

export function useExecuteCampaign(window: string = '30d', options: ExecuteCampaignOptions = {}) {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<CampaignExecuteResponse, Error, CampaignExecuteRequest>({
    mutationFn: executeCampaign,
    onSuccess: (executeResponse, variables) => {
      void queryClient.invalidateQueries({
        queryKey: [...unsubscribeCampaignKeys.all, 'candidates', window],
      });
      toast.success(t('cleanup.unsubscribe.action.submitOk'), {
        description: t('cleanup.unsubscribe.action.submitDescription', {
          count: variables.senderEmails.length,
        }),
      });
      options.onSuccess?.(executeResponse, variables);
    },
    onError: (mutationError) => {
      const { status, errorCode } = extractStatusAndCode(mutationError);
      if (status === 400 && errorCode === 'error.cleanup.campaign.too_many_senders') {
        toast.error(t('cleanup.unsubscribe.action.errCapSender'));
      } else if (status === 400 && errorCode === 'error.cleanup.campaign.too_many_messages') {
        toast.error(t('cleanup.unsubscribe.action.errCapMessage'));
      } else {
        toast.error(t('cleanup.unsubscribe.action.errGeneric'));
      }
    },
  });
}
