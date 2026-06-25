import type { AdminOverviewQueryInput } from './overview-api';

export const overviewQueryKeys = {
  all: ['overview'] as const,
  dashboard: (input: AdminOverviewQueryInput) =>
    [
      ...overviewQueryKeys.all,
      'dashboard',
      input.from.toISOString(),
      input.to.toISOString(),
    ] as const,
};
