'use client';

import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import {
  previewCampaign,
  type CampaignPreviewRequest,
  type CampaignPreviewResponse,
} from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';

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

export function usePreviewCampaign() {
  const t = useTranslations();

  return useMutation<CampaignPreviewResponse, Error, CampaignPreviewRequest>({
    mutationFn: previewCampaign,
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
