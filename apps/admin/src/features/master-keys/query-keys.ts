export const masterKeyQueryKeys = {
  all: ['master-keys'] as const,
  detail: (provider: string) => ['master-keys', provider] as const,
};
