import {
  dateRangeSpecCacheKey,
  type DateRangeSpec,
} from '@/features/cleanup/unsubscribe-campaign/date-range-spec';

export const unsubscribeCampaignKeys = {
  all: ['cleanup', 'unsubscribe-campaign'] as const,
  candidates: (spec: DateRangeSpec, limit?: number) =>
    [
      ...unsubscribeCampaignKeys.all,
      'candidates',
      dateRangeSpecCacheKey(spec),
      limit ?? 'default',
    ] as const,
  // Prefix that matches every candidates page for a given spec — used by
  // mutation invalidation so a Send/Archive flips every cached limit at once.
  candidatesPrefix: (spec: DateRangeSpec) =>
    [...unsubscribeCampaignKeys.all, 'candidates', dateRangeSpecCacheKey(spec)] as const,
  senderTimeline: (senderEmail: string, spec: DateRangeSpec) =>
    [
      ...unsubscribeCampaignKeys.all,
      'stats',
      'timeline',
      senderEmail,
      dateRangeSpecCacheKey(spec),
    ] as const,
  senderMessages: (senderEmail: string, archivedOnly: boolean, spec: DateRangeSpec) =>
    [
      ...unsubscribeCampaignKeys.all,
      'stats',
      'messages',
      senderEmail,
      archivedOnly ? 'archived' : 'all',
      dateRangeSpecCacheKey(spec),
    ] as const,
  senderMessageBody: (gmailMessageId: string) =>
    [...unsubscribeCampaignKeys.all, 'stats', 'body', gmailMessageId] as const,
} as const;
