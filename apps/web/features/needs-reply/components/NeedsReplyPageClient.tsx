'use client';

import { useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

import { NeedsReplyReaderPane } from '@/features/needs-reply/components/NeedsReplyReaderPane';
import { NeedsReplyTable } from '@/features/needs-reply/components/NeedsReplyTable';
import type { NeedsReplyBucket } from '@/features/needs-reply/api/needs-reply-api';
import {
  flattenNeedsReplyRows,
  latestToReplyCount,
  useNeedsReplyInbox,
} from '@/features/needs-reply/hooks/useNeedsReplyInbox';
import { useNeedsReplyCounts } from '@/features/needs-reply/hooks/useNeedsReplyCounts';
import { cn } from '@/lib/utils';
import { useHydrated } from '@/lib/use-hydrated';

const NEEDS_REPLY_TABS = ['to-reply', 'awaiting-their-reply', 'drafted'] as const;

function normalizeBucket(value: string | null): NeedsReplyBucket {
  return NEEDS_REPLY_TABS.includes(value as NeedsReplyBucket)
    ? (value as NeedsReplyBucket)
    : 'to-reply';
}

export function NeedsReplyPageClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const hydrated = useHydrated();
  const activeBucket = normalizeBucket(searchParams.get('tab'));
  const inboxQuery = useNeedsReplyInbox(activeBucket, false);
  const countsQuery = useNeedsReplyCounts();
  const rows = useMemo(() => flattenNeedsReplyRows(inboxQuery.data), [inboxQuery.data]);
  const fallbackToReplyCount = latestToReplyCount(inboxQuery.data);
  const [selectedThreadId, setSelectedThreadId] = useState<string | null>(null);
  const selectedRow = rows.find((row) => row.gmailThreadId === selectedThreadId) ?? null;

  // All three tab badges come from the dedicated counts endpoint so they are accurate regardless of
  // which tab is open. Before hydration (and as a fallback) fall back to the loaded-rows estimate.
  const counts = hydrated ? countsQuery.data : undefined;
  const toReplyCount = counts?.toReply ?? fallbackToReplyCount;
  const awaitingCount =
    counts?.awaiting ?? (activeBucket === 'awaiting-their-reply' ? rows.length : 0);
  const draftedCount = counts?.drafted ?? (activeBucket === 'drafted' ? rows.length : 0);

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden">
      <div className="bg-background grid min-h-0 flex-1 grid-cols-1 overflow-hidden lg:grid-cols-[minmax(390px,42vw)_minmax(0,1fr)] xl:grid-cols-[500px_minmax(0,1fr)]">
        <section
          className={cn(
            'border-border lg:border-r-border min-h-0 flex-col lg:flex lg:border-r lg:border-b-0',
            selectedRow ? 'hidden lg:flex' : 'flex',
          )}
        >
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
            selectedThreadId={selectedThreadId}
            onLoadMore={() => void inboxQuery.fetchNextPage()}
            onRetry={() => void inboxQuery.refetch()}
            onBucketChange={(bucket) => {
              setSelectedThreadId(null);
              router.replace(`/needs-reply?tab=${bucket}`, { scroll: false });
            }}
            onOpenRow={(row) => setSelectedThreadId(row.gmailThreadId)}
          />
        </section>

        <section
          className={cn(
            'min-h-0 min-w-0 overflow-hidden lg:block',
            selectedRow ? 'block' : 'hidden lg:block',
          )}
        >
          <NeedsReplyReaderPane
            row={selectedRow}
            activeBucket={activeBucket}
            onBack={() => setSelectedThreadId(null)}
          />
        </section>
      </div>
    </div>
  );
}
