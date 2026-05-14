'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { NeedsReplyTable } from '@/features/needs-reply/components/NeedsReplyTable';
import type { NeedsReplyBucket } from '@/features/needs-reply/api/needs-reply-api';
import {
  flattenNeedsReplyRows,
  latestToReplyCount,
  useNeedsReplyInbox,
} from '@/features/needs-reply/hooks/useNeedsReplyInbox';
import { useToReplyCount } from '@/features/needs-reply/hooks/useToReplyCount';
import { useHydrated } from '@/lib/use-hydrated';

const NEEDS_REPLY_TABS = ['to-reply', 'awaiting-their-reply'] as const;

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
  const countQuery = useToReplyCount();
  const rows = flattenNeedsReplyRows(inboxQuery.data);
  const fallbackToReplyCount = latestToReplyCount(inboxQuery.data);
  const toReplyCount = hydrated ? (countQuery.data ?? fallbackToReplyCount) : fallbackToReplyCount;

  return (
    <div className="space-y-5">
      <div className="space-y-1">
        <h1 className="text-foreground text-xl font-semibold">{t('needsReply.page.title')}</h1>
        <p className="text-muted-foreground max-w-3xl text-sm leading-6">
          {t('needsReply.page.description')}
        </p>
      </div>

      <NeedsReplyTable
        activeBucket={activeBucket}
        rows={rows}
        toReplyCount={toReplyCount}
        awaitingCount={activeBucket === 'awaiting-their-reply' ? rows.length : 0}
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
      />
    </div>
  );
}
