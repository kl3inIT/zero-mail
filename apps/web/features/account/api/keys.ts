/**
 * Account-domain TanStack Query key factory (Plan 04 — D-B3, D-B5).
 *
 * Per-feature keys keep cross-feature invalidation explicit; never use
 * a global string-based magic key.
 */
export const accountKeys = {
  all: ['account'] as const,
  me: () => [...accountKeys.all, 'me'] as const,
} as const;
