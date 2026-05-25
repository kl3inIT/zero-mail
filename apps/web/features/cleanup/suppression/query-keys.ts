export const suppressionKeys = {
  all: ['cleanup', 'suppression'] as const,
  list: () => [...suppressionKeys.all, 'list'] as const,
} as const;
