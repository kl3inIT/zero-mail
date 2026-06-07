'use client';

import { ArrowLeft, Check, ExternalLink, Inbox, Mail, PenLine } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { EmailHtmlFrame, PlainEmailContent } from '@/features/inbox/components/EmailHtmlFrame';
import {
  useGmailDraftDetail,
  useInboxMessageDetail,
} from '@/features/inbox/hooks/useInboxMessages';
import { GenerateDraftButton } from '@/features/needs-reply/components/GenerateDraftButton';
import { SenderAvatar } from '@/features/needs-reply/components/SenderAvatar';
import type {
  DraftStatus,
  NeedsReplyBucket,
  NeedsReplyRow,
} from '@/features/needs-reply/api/needs-reply-api';
import { useMarkResolved } from '@/features/needs-reply/hooks/useMarkResolved';
import { formatDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';

type NeedsReplyReaderPaneProps = {
  row: NeedsReplyRow | null;
  activeBucket: NeedsReplyBucket;
  onBack?: () => void;
  onResolved?: () => void;
};

export function NeedsReplyReaderPane({
  row,
  activeBucket,
  onBack,
  onResolved,
}: NeedsReplyReaderPaneProps) {
  const t = useTranslations();
  const locale = useLocale();
  const markResolved = useMarkResolved();
  const latestMessageId = row?.latestMessageId ?? null;
  const draftId = row?.draftId ?? null;
  const detailQuery = useInboxMessageDetail(latestMessageId);
  const draftQuery = useGmailDraftDetail(draftId);
  const detailMessage = detailQuery.data?.message ?? null;

  if (!row) {
    return <NeedsReplyUnselectedState />;
  }

  const subject = row.subject || t('triage.audit.message.untitled');
  const senderLine =
    detailMessage?.from || row.otherParty || t('triage.audit.message.unknownSender');
  const receivedAt = detailMessage?.receivedAt ?? row.lastActivityAt;

  return (
    <article
      className="bg-background flex h-full min-h-0 flex-col"
      data-testid="needs-reply-reader"
    >
      <header className="border-border bg-background shrink-0 border-b">
        <div className="border-border flex items-center gap-1 border-b px-2 py-1.5 lg:hidden">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-muted-foreground hover:text-foreground -ml-1 gap-1.5"
            onClick={onBack}
            data-testid="needs-reply-reader-back"
          >
            <ArrowLeft className="size-4" aria-hidden="true" />
            {t('inbox.action.back')}
          </Button>
        </div>

        <div className="px-5 py-4">
          <div className="mb-2 flex flex-wrap items-center gap-1.5">
            <BucketBadge activeBucket={activeBucket} />
            <DraftStatusBadge draftStatus={row.draftStatus} />
          </div>

          <div className="flex min-w-0 items-start justify-between gap-3">
            <h2 className="text-foreground line-clamp-2 min-w-0 flex-1 text-[19px] leading-7 font-semibold tracking-tight">
              {subject}
            </h2>
            <a
              href={row.openInGmailUrl}
              target="_blank"
              rel="noreferrer"
              aria-label={t('needsReply.action.openInGmail')}
              title={t('needsReply.action.openInGmail')}
              className={cn(
                buttonVariants({ variant: 'ghost', size: 'icon-sm' }),
                'text-muted-foreground hover:text-foreground shrink-0',
              )}
            >
              <ExternalLink className="size-4" aria-hidden="true" />
            </a>
          </div>

          <div className="mt-3 flex min-w-0 items-start gap-3">
            <SenderAvatar sender={senderLine} size="lg" />
            <div className="min-w-0 flex-1">
              <div className="flex min-w-0 items-center gap-2">
                <span className="text-foreground min-w-0 truncate text-sm font-medium">
                  {senderLine}
                </span>
                <time
                  className="text-muted-foreground ml-auto shrink-0 text-xs whitespace-nowrap"
                  dateTime={receivedAt}
                >
                  {formatDateTime(receivedAt, locale)}
                </time>
              </div>
              {row.snippet ? (
                <p className="text-muted-foreground mt-0.5 line-clamp-2 text-xs">{row.snippet}</p>
              ) : null}
            </div>
          </div>
        </div>
      </header>

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

      <footer className="border-border bg-background flex shrink-0 flex-wrap items-center gap-2 border-t p-3 sm:p-4">
        {activeBucket !== 'awaiting-their-reply' ? (
          <GenerateDraftButton gmailThreadId={row.gmailThreadId} draftStatus={row.draftStatus} />
        ) : null}
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="gap-1.5"
          disabled={markResolved.isPending}
          onClick={() => markResolved.mutate(row.gmailThreadId, { onSuccess: onResolved })}
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
      </footer>
    </article>
  );
}

function NeedsReplyUnselectedState() {
  const t = useTranslations();
  return (
    <div className="flex h-full min-h-[360px] items-center justify-center p-6">
      <div className="text-muted-foreground flex max-w-sm flex-col items-center gap-2 text-center text-sm">
        <Mail className="size-5" aria-hidden="true" />
        <p className="text-foreground font-medium">{t('needsReply.page.title')}</p>
        <p>{t('needsReply.page.description')}</p>
      </div>
    </div>
  );
}

function BucketBadge({ activeBucket }: { activeBucket: NeedsReplyBucket }) {
  const t = useTranslations();
  const label =
    activeBucket === 'awaiting-their-reply'
      ? t('needsReply.tabs.awaitingReply')
      : activeBucket === 'drafted'
        ? t('needsReply.tabs.drafted')
        : t('needsReply.tabs.toReply');
  return (
    <Badge variant="secondary" className="h-5 gap-1 px-1.5 text-[10px]">
      <Inbox className="size-3" aria-hidden="true" />
      {label}
    </Badge>
  );
}

function DraftStatusBadge({ draftStatus }: { draftStatus: DraftStatus }) {
  const t = useTranslations();
  if (draftStatus === 'NO_DRAFT') return null;

  const sent = draftStatus === 'DRAFT_SENT';
  return (
    <Badge
      variant="outline"
      className={cn(
        'h-5 gap-1 px-1.5 text-[10px]',
        sent
          ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
          : 'border-blue-500/30 bg-blue-500/10 text-blue-700 dark:text-blue-300',
      )}
    >
      <PenLine className="size-3" aria-hidden="true" />
      {sent ? t('needsReply.row.draftSent') : t('needsReply.row.draftWritten')}
    </Badge>
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
