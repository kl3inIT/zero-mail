'use client';

import { useTranslations } from 'next-intl';

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import type { NeedsReplyBucket } from '@/features/needs-reply/api/needs-reply-api';
import { cn } from '@/lib/utils';

type NeedsReplyTabsProps = {
  activeBucket: NeedsReplyBucket;
  toReplyCount: number;
  awaitingCount?: number;
  draftedCount?: number;
  onBucketChange?: (bucket: NeedsReplyBucket) => void;
};

export function NeedsReplyTabs({
  activeBucket,
  toReplyCount,
  awaitingCount = 0,
  draftedCount = 0,
  onBucketChange,
}: NeedsReplyTabsProps) {
  const t = useTranslations();

  return (
    <Tabs
      value={activeBucket}
      onValueChange={(value) => onBucketChange?.(normalizeBucket(value))}
      data-testid="needs-reply-tabs"
      data-overflow="scroll"
    >
      <div className="overflow-x-auto">
        <TabsList className="h-9 min-w-max gap-0.5" aria-label={t('needsReply.tabs.label')}>
          <NeedsReplyTabsTrigger
            value="to-reply"
            label={t('needsReply.tabs.toReply')}
            count={toReplyCount}
            active={activeBucket === 'to-reply'}
            accented
          />
          <NeedsReplyTabsTrigger
            value="awaiting-their-reply"
            label={t('needsReply.tabs.awaitingReply')}
            count={awaitingCount}
            active={activeBucket === 'awaiting-their-reply'}
          />
          <NeedsReplyTabsTrigger
            value="drafted"
            label={t('needsReply.tabs.drafted')}
            count={draftedCount}
            active={activeBucket === 'drafted'}
          />
        </TabsList>
      </div>
    </Tabs>
  );
}

function NeedsReplyTabsTrigger({
  value,
  label,
  count,
  active,
  accented = false,
}: {
  value: NeedsReplyBucket;
  label: string;
  count: number;
  active: boolean;
  accented?: boolean;
}) {
  return (
    <TabsTrigger value={value} className="gap-2 px-3">
      <span>{label}</span>
      <span
        className={cn(
          'inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-xs font-semibold tabular-nums transition-colors',
          active
            ? accented && count > 0
              ? 'bg-primary text-primary-foreground'
              : 'bg-foreground/10 text-foreground'
            : 'bg-foreground/10 text-muted-foreground',
        )}
      >
        {count}
      </span>
    </TabsTrigger>
  );
}

function normalizeBucket(value: string): NeedsReplyBucket {
  if (value === 'awaiting-their-reply') return 'awaiting-their-reply';
  if (value === 'drafted') return 'drafted';
  return 'to-reply';
}
