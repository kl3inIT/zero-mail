export const notificationsKeys = {
  all: ['notifications'] as const,
  preferences: () => [...notificationsKeys.all, 'preferences'] as const,
} as const;
