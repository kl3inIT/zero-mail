/**
 * Gmail-domain TanStack Query key factory (Plan 04 Task 3 — D-B3, D-B5).
 */
export const gmailKeys = {
  all: ['gmail'] as const,
  status: () => [...gmailKeys.all, 'status'] as const,
} as const;
