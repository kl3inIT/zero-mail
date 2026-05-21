export const chatKeys = {
  all: ['chat'] as const,
  list: () => [...chatKeys.all, 'list'] as const,
  detail: (chatId: string) => [...chatKeys.all, 'detail', chatId] as const,
} as const;
