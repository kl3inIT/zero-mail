export const unsubscribeCampaignKeys = {
  all: ['cleanup', 'unsubscribe-campaign'] as const,
  candidates: (window: string) => [...unsubscribeCampaignKeys.all, 'candidates', window] as const,
  byId: (jobId: string) => [...unsubscribeCampaignKeys.all, 'detail', jobId] as const,
} as const;
