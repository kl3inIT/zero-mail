import type { WaitlistListQuery } from './waitlist-api';

export const waitlistQueryKeys = {
  all: ['admin-waitlist'] as const,
  list: (query: WaitlistListQuery) => [...waitlistQueryKeys.all, 'list', query] as const,
};
