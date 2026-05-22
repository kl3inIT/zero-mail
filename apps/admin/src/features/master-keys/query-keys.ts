export const masterKeyQueryKeys = {
  all: ['master-keys'] as const,
  detail: (provider: string) => ['master-keys', provider] as const,
  keys: (provider: string) => ['master-keys', provider, 'keys'] as const,
};
