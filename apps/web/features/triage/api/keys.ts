export const triageKeys = {
  all: ['triage'] as const,
  pause: () => [...triageKeys.all, 'pause'] as const,
} as const;
