'use client';

import { useInfiniteQuery, type InfiniteData } from '@tanstack/react-query';

import {
  getNeedsReplyInbox,
  type NeedsReplyBucket,
  type NeedsReplyPage,
  type NeedsReplyRow,
} from '@/features/needs-reply/api/needs-reply-api';
import { needsReplyKeys } from '@/features/needs-reply/query-keys';

export function useNeedsReplyInbox(bucket: NeedsReplyBucket, resolved = false) {
  return useInfiniteQuery({
    queryKey: needsReplyKeys.inbox(bucket, resolved),
    queryFn: ({ pageParam }) =>
      getNeedsReplyInbox({
        bucket,
        cursor: pageParam,
        limit: 20,
        resolved,
      }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });
}

export function flattenNeedsReplyRows(
  data: InfiniteData<NeedsReplyPage> | undefined,
): NeedsReplyRow[] {
  return data?.pages.flatMap((page) => page.items) ?? [];
}

export function latestToReplyCount(data: InfiniteData<NeedsReplyPage> | undefined): number {
  return data?.pages[0]?.toReplyCount ?? 0;
}
