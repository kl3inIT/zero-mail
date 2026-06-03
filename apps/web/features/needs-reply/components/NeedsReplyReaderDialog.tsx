'use client';

import { Check, ExternalLink, PenLine } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button, buttonVariants } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { EmailHtmlFrame, PlainEmailContent } from '@/features/inbox/components/EmailHtmlFrame';
import {
  useGmailDraftDetail,
  useInboxMessageDetail,
} from '@/features/inbox/hooks/useInboxMessages';
import { GenerateDraftButton } from '@/features/needs-reply/components/GenerateDraftButton';
import { SenderAvatar } from '@/features/needs-reply/components/SenderAvatar';
import type { NeedsReplyBucket, NeedsReplyRow } from '@/features/needs-reply/api/needs-reply-api';
import { useMarkResolved } from '@/features/needs-reply/hooks/useMarkResolved';
import { formatDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';

type NeedsReplyReaderDialogProps = {
  row: NeedsReplyRow | null;
  activeBucket: NeedsReplyBucket;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export function NeedsReplyReaderDialog({
  row,
  activeBucket,
  open,
  onOpenChange,
}: NeedsReplyReaderDialogProps) {
  const t = useTranslations();
  const locale = useLocale();
  const markResolved = useMarkResolved();
  // Only fetch while the dialog is open for this row — the hook keys on the message id and reuses
  // the inbox detail cache, so opening a thread already read in the inbox is instant.
  const latestMessageId = open ? (row?.latestMessageId ?? null) : null;
  const draftId = open ? (row?.draftId ?? null) : null;
  const detailQuery = useInboxMessageDetail(latestMessageId);
  const draftQuery = useGmailDraftDetail(draftId);
  const detailMessage = detailQuery.data?.message ?? null;

  const subject = row?.subject || t('triage.audit.message.untitled');
  const senderLine =
    detailMessage?.from || row?.otherParty || t('triage.audit.message.unknownSender');
  const receivedAt = detailMessage?.receivedAt ?? row?.lastActivityAt ?? null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="flex max-h-[85vh] w-full flex-col gap-0 overflow-hidden p-0 sm:max-w-3xl"
        data-testid="needs-reply-reader"
      >
        <DialogHeader className="border-border gap-3 border-b p-4 pr-12">
          <div className="flex min-w-0 items-start gap-3">
            <SenderAvatar sender={senderLine} size="lg" />
            <div className="min-w-0 flex-1">
              <DialogTitle className="line-clamp-2 text-[17px] leading-6">{subject}</DialogTitle>
              <DialogDescription className="mt-1 min-w-0 truncate">{senderLine}</DialogDescription>
              {receivedAt ? (
                <time className="text-muted-foreground mt-0.5 block text-xs" dateTime={receivedAt}>
                  {formatDateTime(receivedAt, locale)}
                </time>
              ) : null}
            </div>
          </div>
        </DialogHeader>

        <div
          className="min-h-0 flex-1 overflow-y-auto bg-white"
          data-testid="needs-reply-reader-body"
        >
          {draftId ? (
            <>
              <DraftSection
                isLoading={draftQuery.isPending}
                error={draftQuery.error}
                renderedHtml={draftQuery.data?.renderedHtml ?? ''}
                renderedText={draftQuery.data?.renderedText ?? ''}
                subject={subject}
                locale={locale}
              />
              <div className="text-muted-foreground border-border bg-muted/40 border-y px-5 py-1.5 text-[11px] font-semibold tracking-wide uppercase">
                {t('needsReply.reader.originalHeading')}
              </div>
            </>
          ) : null}
          <ReaderBody
            latestMessageId={latestMessageId}
            isLoading={detailQuery.isPending && Boolean(latestMessageId)}
            error={detailQuery.error}
            renderedHtml={detailQuery.data?.renderedHtml ?? ''}
            renderedText={detailQuery.data?.renderedText ?? ''}
            subject={subject}
            locale={locale}
          />
        </div>

        {row ? (
          <div className="border-border flex flex-wrap items-center gap-2 border-t p-4">
            {activeBucket !== 'awaiting-their-reply' ? (
              <GenerateDraftButton
                gmailThreadId={row.gmailThreadId}
                draftStatus={row.draftStatus}
              />
            ) : null}
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="gap-1.5"
              disabled={markResolved.isPending}
              onClick={() =>
                markResolved.mutate(row.gmailThreadId, { onSuccess: () => onOpenChange(false) })
              }
            >
              <Check className="size-3.5" aria-hidden="true" />
              {t('needsReply.action.markResolved')}
            </Button>
            <a
              href={row.openInGmailUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={cn(
                buttonVariants({ variant: 'ghost', size: 'sm' }),
                'text-muted-foreground ml-auto gap-1.5',
              )}
            >
              <ExternalLink className="size-3.5" aria-hidden="true" />
              {t('needsReply.action.openInGmail')}
            </a>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function DraftSection({
  isLoading,
  error,
  renderedHtml,
  renderedText,
  subject,
  locale,
}: {
  isLoading: boolean;
  error: unknown;
  renderedHtml: string;
  renderedText: string;
  subject: string;
  locale: string;
}) {
  const t = useTranslations();
  const readableText = renderedText.trim();

  return (
    <section
      className="bg-primary/5 border-primary/15 border-b px-5 py-4"
      data-testid="needs-reply-draft-section"
    >
      <div className="text-primary mb-2 flex items-center gap-1.5 text-xs font-semibold tracking-wide uppercase">
        <PenLine className="size-3.5" aria-hidden="true" />
        {t('needsReply.reader.draftHeading')}
      </div>
      {isLoading ? (
        <div className="space-y-2" aria-busy="true">
          <Skeleton className="h-4 w-10/12" />
          <Skeleton className="h-4 w-8/12" />
        </div>
      ) : error ? (
        <p className="text-muted-foreground text-sm">{t('needsReply.reader.draftUnavailable')}</p>
      ) : readableText ? (
        <div className="text-foreground/90 text-sm leading-6 break-words whitespace-pre-wrap">
          {readableText}
        </div>
      ) : renderedHtml ? (
        <div className="overflow-hidden rounded-md border bg-white">
          <EmailHtmlFrame renderedHtml={renderedHtml} title={subject} locale={locale} />
        </div>
      ) : (
        <p className="text-muted-foreground text-sm">{t('needsReply.reader.draftUnavailable')}</p>
      )}
    </section>
  );
}

function ReaderBody({
  latestMessageId,
  isLoading,
  error,
  renderedHtml,
  renderedText,
  subject,
  locale,
}: {
  latestMessageId: string | null;
  isLoading: boolean;
  error: unknown;
  renderedHtml: string;
  renderedText: string;
  subject: string;
  locale: string;
}) {
  const t = useTranslations();

  if (!latestMessageId) {
    return (
      <div className="p-5">
        <Alert>
          <AlertTitle>{t('needsReply.reader.unavailableTitle')}</AlertTitle>
          <AlertDescription>{t('needsReply.reader.unavailableBody')}</AlertDescription>
        </Alert>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-3 p-5" aria-busy="true">
        <Skeleton className="h-4 w-11/12" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-10/12" />
        <Skeleton className="h-4 w-9/12" />
        <Skeleton className="h-4 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-5">
        <Alert variant="destructive">
          <AlertTitle>{t('needsReply.error.title')}</AlertTitle>
          <AlertDescription>{t('needsReply.error.body')}</AlertDescription>
        </Alert>
      </div>
    );
  }

  const readableText = renderedText.trim();
  if (renderedHtml) {
    return <EmailHtmlFrame renderedHtml={renderedHtml} title={subject} locale={locale} />;
  }
  if (readableText) {
    return <PlainEmailContent text={readableText} />;
  }
  return <p className="text-muted-foreground p-5 text-sm">{t('needsReply.reader.noBody')}</p>;
}
