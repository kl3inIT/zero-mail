'use client';

import { Loader2 } from 'lucide-react';
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
}: NeedsReplyTableProps) {
  const t = useTranslations();

  return (
    <div className="space-y-3">
      <NeedsReplyTabs
        activeBucket={activeBucket}
        toReplyCount={toReplyCount}
        awaitingCount={awaitingCount}
        draftedCount={draftedCount}
        onBucketChange={onBucketChange}
      />

      {isClassifying ? (
        <Alert variant="warning" className="border-warning/40 bg-warning/5">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          <AlertDescription>{t('needsReply.banner.updating')}</AlertDescription>
        </Alert>
      ) : null}

      {error ? (
        <Alert variant="destructive" className="min-h-32">
          <AlertTitle>{t('needsReply.error.title')}</AlertTitle>
          <AlertDescription>{t('needsReply.error.body')}</AlertDescription>
          <AlertAction>
            <Button type="button" variant="outline" size="sm" onClick={onRetry}>
              {t('needsReply.error.retry')}
            </Button>
          </AlertAction>
        </Alert>
      ) : isLoading ? (
        <NeedsReplySkeletonRows />
      ) : rows.length === 0 ? (
        <NeedsReplyEmptyState activeBucket={activeBucket} />
      ) : (
        <>
          <div className="grid gap-2">
            {rows.map((row) => (
              <NeedsReplyRow key={row.gmailThreadId} row={row} activeBucket={activeBucket} />
            ))}
          </div>
          {hasNextPage ? (
            <div className="flex justify-center">
              <Button
                type="button"
                variant="outline"
                onClick={onLoadMore}
                disabled={isFetchingNextPage}
              >
                {isFetchingNextPage ? t('triage.audit.loadingMore') : t('triage.audit.loadMore')}
              </Button>
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}

function NeedsReplySkeletonRows() {
  return (
    <div className="grid gap-2" aria-busy="true">
      {Array.from({ length: 5 }).map((_, index) => (
        <div
          key={index}
          className="bg-card flex gap-3 rounded-lg border p-4"
          data-testid="needs-reply-skeleton-row"
        >
          <Skeleton className="h-auto w-[3px] shrink-0 rounded-full" />
          <Skeleton className="size-9 shrink-0 rounded-full" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-7 w-48" />
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
              ✓
            </Badge>
          ) : null}
        </span>
      }
      body={body}
    />
  );
}
