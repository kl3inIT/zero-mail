'use client';

import { useQuery } from '@tanstack/react-query';

import { getToReplyCount } from '@/features/needs-reply/api/needs-reply-api';
import { needsReplyKeys } from '@/features/needs-reply/query-keys';

export function useToReplyCount() {
  return useQuery({
    queryKey: needsReplyKeys.count(),
    queryFn: getToReplyCount,
    staleTime: 60_000,
  });
}
