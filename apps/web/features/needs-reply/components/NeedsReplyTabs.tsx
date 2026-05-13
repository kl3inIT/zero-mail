'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import type { NeedsReplyBucket } from '@/features/needs-reply/api/needs-reply-api';
import { cn } from '@/lib/utils';

type NeedsReplyTabsProps = {
  activeBucket: NeedsReplyBucket;
  toReplyCount: number;
  awaitingCount?: number;
  onBucketChange?: (bucket: NeedsReplyBucket) => void;
};

export function NeedsReplyTabs({
  activeBucket,
  toReplyCount,
  awaitingCount = 0,
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
      <div className="overflow-x-auto pb-1">
        <TabsList variant="line" className="min-w-max" aria-label={t('needsReply.tabs.label')}>
          <NeedsReplyTabsTrigger
            value="to-reply"
            label={t('needsReply.tabs.toReply')}
            count={toReplyCount}
            accented
          />
          <NeedsReplyTabsTrigger
            value="awaiting-their-reply"
            label={t('needsReply.tabs.awaitingReply')}
            count={awaitingCount}
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
  accented = false,
}: {
  value: NeedsReplyBucket;
  label: string;
  count: number;
  accented?: boolean;
}) {
  return (
    <TabsTrigger value={value} className="gap-2 px-3">
      <span>{label}</span>
      <Badge
        variant="secondary"
        className={cn(
          'font-mono',
          accented && count > 0 && 'bg-primary/10 text-primary dark:text-primary-foreground',
        )}
      >
        {count}
      </Badge>
    </TabsTrigger>
  );
}

function normalizeBucket(value: string): NeedsReplyBucket {
  return value === 'awaiting-their-reply' ? 'awaiting-their-reply' : 'to-reply';
}
