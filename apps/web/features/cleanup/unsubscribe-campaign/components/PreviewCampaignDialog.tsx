'use client';

import { Loader2 } from 'lucide-react';
import { useEffect } from 'react';
import { useTranslations } from 'next-intl';

import { Alert, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Skeleton } from '@/components/ui/skeleton';
import type {
  CampaignPreviewResponse,
  PerSenderPreviewResponse,
} from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { useExecuteCampaign } from '@/features/cleanup/unsubscribe-campaign/hooks/useExecuteCampaign';
import { usePreviewCampaign } from '@/features/cleanup/unsubscribe-campaign/hooks/usePreviewCampaign';
import { RiskBadge } from '@/features/cleanup/unsubscribe-campaign/components/RiskBadge';

const CAMPAIGN_SENDER_CAP = 25;
const CAMPAIGN_MESSAGE_CAP = 2000;

export function PreviewCampaignDialog({
  open,
  onOpenChange,
  senderEmails,
}: {
  open: boolean;
  onOpenChange: (next: boolean) => void;
  senderEmails: string[];
}) {
  const t = useTranslations();
  const {
    data: previewData,
    isPending: previewIsPending,
    mutate: previewCampaign,
    reset: resetPreview,
  } = usePreviewCampaign();
  const execute = useExecuteCampaign();

  useEffect(() => {
    if (!open) return;
    resetPreview();
    if (senderEmails.length > 0) {
      previewCampaign({ senderEmails });
    }
  }, [open, previewCampaign, resetPreview, senderEmails]);

  useEffect(() => {
    if (!open) {
      resetPreview();
    }
  }, [open, resetPreview]);

  const typedPreviewData: CampaignPreviewResponse | undefined = previewData;
  const perSender: PerSenderPreviewResponse[] = typedPreviewData?.perSender ?? [];
  const totalSenderCount = senderEmails.length;
  const totalMailCount = typedPreviewData?.totalHistoryCount ?? 0;
  const overSenderCap = totalSenderCount > CAMPAIGN_SENDER_CAP;
  const overMessageCap = totalMailCount > CAMPAIGN_MESSAGE_CAP;
  const noPreviewableSenders =
    !previewIsPending &&
    senderEmails.length > 0 &&
    typedPreviewData !== undefined &&
    perSender.length === 0;
  const executeDisabled =
    senderEmails.length === 0 ||
    previewIsPending ||
    execute.isPending ||
    !typedPreviewData ||
    perSender.length === 0 ||
    overSenderCap ||
    overMessageCap;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100vh-2rem)] max-w-2xl overflow-hidden sm:max-w-2xl">
        <DialogHeader className="pr-8">
          <DialogTitle>{t('cleanup.unsubscribe.preview.title')}</DialogTitle>
          <DialogDescription>{t('cleanup.unsubscribe.preview.description')}</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-1 text-sm">
          <p className="text-muted-foreground">
            {t('cleanup.unsubscribe.preview.totalSender', { count: totalSenderCount })}
          </p>
          <p className="text-muted-foreground">
            {t('cleanup.unsubscribe.preview.totalMail', { count: totalMailCount })}
          </p>
        </div>

        {overSenderCap && (
          <Alert variant="destructive">
            <AlertTitle>{t('cleanup.unsubscribe.preview.capSender')}</AlertTitle>
          </Alert>
        )}
        {overMessageCap && (
          <Alert variant="destructive">
            <AlertTitle>
              {t('cleanup.unsubscribe.preview.capMessage', { count: totalMailCount })}
            </AlertTitle>
          </Alert>
        )}
        {noPreviewableSenders && (
          <Alert>
            <AlertTitle>{t('cleanup.unsubscribe.preview.empty')}</AlertTitle>
          </Alert>
        )}

        <ScrollArea className="max-h-[min(56vh,420px)]">
          <div className="flex flex-col gap-2 pr-2">
            {previewIsPending && (
              <>
                {Array.from({ length: Math.min(senderEmails.length, 5) }).map((_, idx) => (
                  <Skeleton key={idx} className="h-12 w-full" />
                ))}
              </>
            )}
            {!previewIsPending &&
              perSender.map((senderPreview) => (
                <div
                  key={senderPreview.senderEmail}
                  className="ring-foreground/10 flex min-w-0 flex-col gap-1 rounded-md p-2 ring-1"
                >
                  <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <span className="min-w-0 font-medium break-all sm:truncate">
                      {senderPreview.senderEmail}
                    </span>
                    <RiskBadge
                      risk={senderPreview.riskBadge ?? 'SAFE'}
                      className="w-fit shrink-0 whitespace-nowrap"
                    />
                  </div>
                  <p className="text-muted-foreground text-xs tabular-nums">
                    {senderPreview.willArchive
                      ? t('cleanup.unsubscribe.preview.willArchive', {
                          count: senderPreview.historyMessageCount ?? 0,
                        })
                      : t('cleanup.unsubscribe.preview.willNotArchive')}
                  </p>
                </div>
              ))}
          </div>
        </ScrollArea>

        <DialogFooter className="gap-2">
          <Button
            type="button"
            variant="outline"
            className="w-full sm:w-auto"
            onClick={() => onOpenChange(false)}
            disabled={execute.isPending}
          >
            {t('cleanup.unsubscribe.preview.cancel')}
          </Button>
          <Button
            type="button"
            variant="default"
            className="w-full sm:w-auto"
            disabled={executeDisabled}
            onClick={() => execute.mutate({ senderEmails })}
          >
            {execute.isPending ? (
              <>
                <Loader2 className="animate-spin" />
                {t('cleanup.unsubscribe.preview.submitting')}
              </>
            ) : (
              t('cleanup.unsubscribe.preview.confirm')
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
