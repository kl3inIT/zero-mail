export const telegramQueryKeys = {
  all: ['telegram'] as const,
  status: () => [...telegramQueryKeys.all, 'status'] as const,
} as const;
