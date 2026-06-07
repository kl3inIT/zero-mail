'use client';

import type { UIEvent } from 'react';
import { Check, Loader2, Reply } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { EmptyState } from '@/components/states/EmptyState';
import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { NeedsReplyRow } from '@/features/needs-reply/components/NeedsReplyRow';
import { NeedsReplyTabs } from '@/features/needs-reply/components/NeedsReplyTabs';
import type {
  NeedsReplyBucket,
  NeedsReplyRow as NeedsReplyRowModel,
} from '@/features/needs-reply/api/needs-reply-api';

type NeedsReplyTableProps = {
  activeBucket: NeedsReplyBucket;
  rows: NeedsReplyRowModel[];
  toReplyCount: number;
  awaitingCount?: number;
  draftedCount?: number;
  isLoading?: boolean;
  isClassifying?: boolean;
  error?: unknown;
  hasNextPage?: boolean;
  isFetchingNextPage?: boolean;
  onLoadMore?: () => void;
  onRetry?: () => void;
  onBucketChange?: (bucket: NeedsReplyBucket) => void;
  onOpenRow?: (row: NeedsReplyRowModel) => void;
  selectedThreadId?: string | null;
};

export function NeedsReplyTable({
  activeBucket,
  rows,
  toReplyCount,
  awaitingCount = activeBucket === 'awaiting-their-reply' ? rows.length : 0,
  draftedCount = activeBucket === 'drafted' ? rows.length : 0,
  isLoading = false,
  isClassifying = false,
  error,
  hasNextPage = false,
  isFetchingNextPage = false,
  onLoadMore,
  onRetry,
  onBucketChange,
  onOpenRow,
  selectedThreadId,
}: NeedsReplyTableProps) {
  const t = useTranslations();

  function handleListScroll(event: UIEvent<HTMLDivElement>): void {
    if (!hasNextPage || isFetchingNextPage) return;
    const listElement = event.currentTarget;
    const distanceFromBottom =
      listElement.scrollHeight - listElement.scrollTop - listElement.clientHeight;
    if (distanceFromBottom < 160) {
      onLoadMore?.();
    }
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="border-border shrink-0 border-b px-4 py-2.5">
        <div className="flex min-h-8 items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2.5">
            <span className="flex size-8 shrink-0 items-center justify-center">
              <Reply className="text-muted-foreground size-5" aria-hidden="true" />
            </span>
            <div className="min-w-0">
              <h1 className="text-foreground truncate text-sm font-medium">
                {t('needsReply.page.title')}
              </h1>
              <p className="text-muted-foreground hidden truncate text-xs sm:block">
                {t(bucketHintKey(activeBucket))}
              </p>
            </div>
          </div>
        </div>

        <div className="mt-2">
          <NeedsReplyTabs
            activeBucket={activeBucket}
            toReplyCount={toReplyCount}
            awaitingCount={awaitingCount}
            draftedCount={draftedCount}
            onBucketChange={onBucketChange}
          />
        </div>
      </div>

      {isClassifying ? (
        <Alert variant="warning" className="border-warning/40 bg-warning/5 m-3 shrink-0 py-2">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          <AlertDescription>{t('needsReply.banner.updating')}</AlertDescription>
        </Alert>
      ) : null}

      <div
        className="min-h-0 flex-1 overflow-y-auto"
        onScroll={handleListScroll}
        data-testid="needs-reply-list"
      >
        {error ? (
          <div className="p-3">
            <Alert variant="destructive" className="min-h-32">
              <AlertTitle>{t('needsReply.error.title')}</AlertTitle>
              <AlertDescription>{t('needsReply.error.body')}</AlertDescription>
              <AlertAction>
                <Button type="button" variant="outline" size="sm" onClick={onRetry}>
                  {t('needsReply.error.retry')}
                </Button>
              </AlertAction>
            </Alert>
          </div>
        ) : isLoading ? (
          <NeedsReplySkeletonRows />
        ) : rows.length === 0 ? (
          <div className="p-3">
            <NeedsReplyEmptyState activeBucket={activeBucket} />
          </div>
        ) : (
          <>
            <div>
              {rows.map((row) => (
                <NeedsReplyRow
                  key={row.gmailThreadId}
                  row={row}
                  selected={row.gmailThreadId === selectedThreadId}
                  onOpen={() => onOpenRow?.(row)}
                />
              ))}
            </div>
            {hasNextPage ? (
              <div className="flex justify-center p-3">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={onLoadMore}
                  disabled={isFetchingNextPage}
                >
                  {isFetchingNextPage ? (
                    <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                  ) : null}
                  {isFetchingNextPage ? t('triage.audit.loadingMore') : t('triage.audit.loadMore')}
                </Button>
              </div>
            ) : null}
          </>
        )}
      </div>
    </div>
  );
}

function bucketHintKey(
  activeBucket: NeedsReplyBucket,
):
  | 'needsReply.tabs.toReplyHint'
  | 'needsReply.tabs.awaitingReplyHint'
  | 'needsReply.tabs.draftedHint' {
  if (activeBucket === 'awaiting-their-reply') return 'needsReply.tabs.awaitingReplyHint';
  if (activeBucket === 'drafted') return 'needsReply.tabs.draftedHint';
  return 'needsReply.tabs.toReplyHint';
}

function NeedsReplySkeletonRows() {
  return (
    <div aria-busy="true">
      {Array.from({ length: 5 }).map((_, index) => (
        <div
          key={index}
          className="border-border flex min-h-[82px] gap-2.5 border-b px-4 py-3"
          data-testid="needs-reply-skeleton-row"
        >
          <Skeleton className="h-auto w-[3px] shrink-0 rounded-full" />
          <Skeleton className="size-8 shrink-0 rounded-full" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-3.5 w-1/3" />
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-7 w-56" />
          </div>
        </div>
      ))}
    </div>
  );
}

function NeedsReplyEmptyState({ activeBucket }: { activeBucket: NeedsReplyBucket }) {
  const t = useTranslations();
  const heading =
    activeBucket === 'to-reply'
      ? t('needsReply.empty.toReply.title')
      : activeBucket === 'awaiting-their-reply'
        ? t('needsReply.empty.awaiting.title')
        : t('needsReply.empty.drafted.title');
  const body =
    activeBucket === 'to-reply'
      ? t('needsReply.empty.toReply.body')
      : activeBucket === 'awaiting-their-reply'
        ? t('needsReply.empty.awaiting.body')
        : t('needsReply.empty.drafted.body');

  return (
    <EmptyState
      heading={
        <span className="inline-flex items-center gap-2">
          {heading}
          {activeBucket === 'to-reply' ? (
            <Badge variant="outline" className="bg-emerald-500/10 text-emerald-700">
              <Check className="size-3" aria-hidden="true" />
            </Badge>
          ) : null}
        </span>
      }
      body={body}
    />
  );
}
