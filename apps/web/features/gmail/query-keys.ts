export const gmailQueryKeys = {
  all: ['gmail'] as const,
  status: () => [...gmailQueryKeys.all, 'status'] as const,
} as const;
