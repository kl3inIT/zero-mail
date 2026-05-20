import type { SpendQueryInput } from './spend-api';

export const spendQueryKeys = {
  all: ['spend'] as const,
  dashboard: (input: SpendQueryInput) =>
    [
      ...spendQueryKeys.all,
      'dashboard',
      input.from.toISOString(),
      input.to.toISOString(),
      (input.providers ?? []).sort().join(','),
      (input.features ?? []).sort().join(','),
    ] as const,
};
