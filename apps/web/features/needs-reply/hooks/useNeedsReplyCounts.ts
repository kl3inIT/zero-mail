'use client';

import { useQuery } from '@tanstack/react-query';

import { getNeedsReplyCounts } from '@/features/needs-reply/api/needs-reply-api';
import { needsReplyKeys } from '@/features/needs-reply/query-keys';

export function useNeedsReplyCounts() {
  return useQuery({
    queryKey: needsReplyKeys.counts(),
    queryFn: getNeedsReplyCounts,
    staleTime: 30_000,
  });
}
