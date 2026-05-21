export const queueQueryKeys = {
  all: ['queue'] as const,
  health: () => [...queueQueryKeys.all, 'health'] as const,
  deadLetters: (cursor: string | null, limit: number) =>
    [...queueQueryKeys.all, 'dead-letters', cursor, limit] as const,
};
