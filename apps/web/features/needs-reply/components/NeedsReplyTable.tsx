'use client';

import { Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { EmptyState } from '@/components/states/EmptyState';
import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableHead, TableHeader, TableRow } from '@/components/ui/table';
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
          <div className="bg-card hidden rounded-lg border md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('needsReply.columns.subject')}</TableHead>
                  <TableHead>{t('needsReply.columns.otherParty')}</TableHead>
                  <TableHead>{t('needsReply.columns.lastActivity')}</TableHead>
                  <TableHead>{t('needsReply.columns.draft')}</TableHead>
                  <TableHead className="text-right">{t('needsReply.columns.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <NeedsReplyRow key={row.gmailThreadId} row={row} activeBucket={activeBucket} />
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="grid gap-3 md:hidden">
            {rows.map((row) => (
              <NeedsReplyRow
                key={row.gmailThreadId}
                row={row}
                activeBucket={activeBucket}
                variant="mobile"
              />
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
    <div className="space-y-2" aria-busy="true">
      {Array.from({ length: 6 }).map((_, index) => (
        <div
          key={index}
          className="bg-card grid grid-cols-[1fr_auto] gap-3 rounded-lg border p-3 md:grid-cols-[2fr_1fr_8rem_7rem_12rem]"
          data-testid="needs-reply-skeleton-row"
        >
          <Skeleton className="h-4 w-4/5" />
          <Skeleton className="h-4 w-24" />
          <Skeleton className="hidden h-4 w-16 md:block" />
          <Skeleton className="hidden h-5 w-20 md:block" />
          <Skeleton className="hidden h-7 w-36 justify-self-end md:block" />
        </div>
      ))}
    </div>
  );
}

function NeedsReplyEmptyState({ activeBucket }: { activeBucket: NeedsReplyBucket }) {
  const t = useTranslations();
  const toReply = activeBucket === 'to-reply';

  return (
    <EmptyState
      heading={
        <span className="inline-flex items-center gap-2">
          {toReply ? t('needsReply.empty.toReply.title') : t('needsReply.empty.awaiting.title')}
          {toReply ? (
            <Badge variant="outline" className="bg-emerald-500/10 text-emerald-700">
              ✓
            </Badge>
          ) : null}
        </span>
      }
      body={toReply ? t('needsReply.empty.toReply.body') : t('needsReply.empty.awaiting.body')}
    />
  );
}
