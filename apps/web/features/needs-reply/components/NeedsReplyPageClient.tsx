'use client';

import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { NeedsReplyReaderDialog } from '@/features/needs-reply/components/NeedsReplyReaderDialog';
import { NeedsReplyTable } from '@/features/needs-reply/components/NeedsReplyTable';
import type { NeedsReplyBucket, NeedsReplyRow } from '@/features/needs-reply/api/needs-reply-api';
import {
  flattenNeedsReplyRows,
  latestToReplyCount,
  useNeedsReplyInbox,
} from '@/features/needs-reply/hooks/useNeedsReplyInbox';
import { useNeedsReplyCounts } from '@/features/needs-reply/hooks/useNeedsReplyCounts';
import { useHydrated } from '@/lib/use-hydrated';

const NEEDS_REPLY_TABS = ['to-reply', 'awaiting-their-reply', 'drafted'] as const;

function normalizeBucket(value: string | null): NeedsReplyBucket {
  return NEEDS_REPLY_TABS.includes(value as NeedsReplyBucket)
    ? (value as NeedsReplyBucket)
    : 'to-reply';
}

export function NeedsReplyPageClient() {
  const t = useTranslations();
  const router = useRouter();
  const searchParams = useSearchParams();
  const hydrated = useHydrated();
  const activeBucket = normalizeBucket(searchParams.get('tab'));
  const inboxQuery = useNeedsReplyInbox(activeBucket, false);
  const countsQuery = useNeedsReplyCounts();
  const rows = flattenNeedsReplyRows(inboxQuery.data);
  const fallbackToReplyCount = latestToReplyCount(inboxQuery.data);
  const [selectedRow, setSelectedRow] = useState<NeedsReplyRow | null>(null);
  const [readerOpen, setReaderOpen] = useState(false);

  // All three tab badges come from the dedicated counts endpoint so they are accurate regardless of
  // which tab is open. Before hydration (and as a fallback) fall back to the loaded-rows estimate.
  const counts = hydrated ? countsQuery.data : undefined;
  const toReplyCount = counts?.toReply ?? fallbackToReplyCount;
  const awaitingCount =
    counts?.awaiting ?? (activeBucket === 'awaiting-their-reply' ? rows.length : 0);
  const draftedCount = counts?.drafted ?? (activeBucket === 'drafted' ? rows.length : 0);

  return (
    <div className="mx-auto w-full max-w-4xl space-y-5">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t('needsReply.page.title')}</h1>
        <p className="text-muted-foreground text-sm">{t('needsReply.page.description')}</p>
      </header>

      <NeedsReplyTable
        activeBucket={activeBucket}
        rows={rows}
        toReplyCount={toReplyCount}
        awaitingCount={awaitingCount}
        draftedCount={draftedCount}
        isLoading={inboxQuery.isPending}
        isClassifying={inboxQuery.isFetching && !inboxQuery.isPending}
        error={inboxQuery.error}
        hasNextPage={Boolean(inboxQuery.hasNextPage)}
        isFetchingNextPage={inboxQuery.isFetchingNextPage}
        onLoadMore={() => void inboxQuery.fetchNextPage()}
        onRetry={() => void inboxQuery.refetch()}
        onBucketChange={(bucket) => {
          router.replace(`/needs-reply?tab=${bucket}`, { scroll: false });
        }}
        onOpenRow={(row) => {
          setSelectedRow(row);
          setReaderOpen(true);
        }}
      />

      <NeedsReplyReaderDialog
        row={selectedRow}
        activeBucket={activeBucket}
        open={readerOpen}
        onOpenChange={(open) => {
          setReaderOpen(open);
          if (!open) setSelectedRow(null);
        }}
      />
    </div>
  );
}
