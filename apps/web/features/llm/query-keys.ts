// TanStack Query key factory for the LLM/BYOK feature.
// Per CLAUDE.md convention #8 — one query-keys.ts per feature owning cached data.

export const byokKeys = {
  all: ['byok'] as const,
  current: () => [...byokKeys.all, 'current'] as const,
};
