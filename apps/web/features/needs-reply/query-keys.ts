import type { NeedsReplyBucket } from '@/features/needs-reply/api/needs-reply-api';

export const needsReplyKeys = {
  all: ['needs-reply'] as const,
  inbox: (bucket: NeedsReplyBucket, resolved = false) =>
    [...needsReplyKeys.all, 'inbox', bucket, resolved] as const,
  count: () => [...needsReplyKeys.all, 'count'] as const,
  counts: () => [...needsReplyKeys.all, 'counts'] as const,
} as const;
