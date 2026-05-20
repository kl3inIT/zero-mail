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
  const preview = usePreviewCampaign();
  const execute = useExecuteCampaign();

  // Trigger preview fetch when dialog opens with a non-empty selection.
  useEffect(() => {
    if (open && senderEmails.length > 0 && !preview.isPending && !preview.data) {
      preview.mutate({ senderEmails });
    }
    // We intentionally only react to `open` toggling; senderEmails are captured at open time.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Reset when closing.
  useEffect(() => {
    if (!open) {
      preview.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const previewData: CampaignPreviewResponse | undefined = preview.data;
  const perSender: PerSenderPreviewResponse[] = previewData?.perSender ?? [];
  const totalSenderCount = senderEmails.length;
  const totalMailCount = previewData?.totalHistoryCount ?? 0;
  const overSenderCap = totalSenderCount > CAMPAIGN_SENDER_CAP;
  const overMessageCap = totalMailCount > CAMPAIGN_MESSAGE_CAP;
  const executeDisabled = preview.isPending || execute.isPending || overSenderCap || overMessageCap;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
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

        <ScrollArea className="max-h-[60vh]">
          <div className="flex flex-col gap-2 pr-2">
            {preview.isPending && (
              <>
                {Array.from({ length: Math.min(senderEmails.length, 5) }).map((_, idx) => (
                  <Skeleton key={idx} className="h-12 w-full" />
                ))}
              </>
            )}
            {!preview.isPending &&
              perSender.map((senderPreview) => (
                <div
                  key={senderPreview.senderEmail}
                  className="ring-foreground/10 flex flex-col gap-1 rounded-md p-2 ring-1"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate font-medium">{senderPreview.senderEmail}</span>
                    <RiskBadge risk={senderPreview.riskBadge ?? 'SAFE'} />
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

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={execute.isPending}
          >
            {t('cleanup.unsubscribe.preview.cancel')}
          </Button>
          <Button
            type="button"
            variant="default"
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
