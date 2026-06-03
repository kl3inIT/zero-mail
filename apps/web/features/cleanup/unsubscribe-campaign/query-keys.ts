export const unsubscribeCampaignKeys = {
  all: ['cleanup', 'unsubscribe-campaign'] as const,
  candidates: (window: string, limit?: number) =>
    [...unsubscribeCampaignKeys.all, 'candidates', window, limit ?? 'default'] as const,
  senderTimeline: (senderEmail: string, windowDays: number) =>
    [...unsubscribeCampaignKeys.all, 'stats', 'timeline', senderEmail, windowDays] as const,
  senderMessages: (senderEmail: string, archivedOnly: boolean) =>
    [
      ...unsubscribeCampaignKeys.all,
      'stats',
      'messages',
      senderEmail,
      archivedOnly ? 'archived' : 'all',
    ] as const,
  senderMessageBody: (gmailMessageId: string) =>
    [...unsubscribeCampaignKeys.all, 'stats', 'body', gmailMessageId] as const,
} as const;
