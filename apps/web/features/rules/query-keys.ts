export const rulesKeys = {
  all: ['rules'] as const,
  list: () => [...rulesKeys.all, 'list'] as const,
  detail: (ruleId: string) => [...rulesKeys.all, 'detail', ruleId] as const,
  catalog: () => [...rulesKeys.all, 'catalog'] as const,
  catalogExamples: (locale: string) => [...rulesKeys.catalog(), 'examples', locale] as const,
  catalogActions: (locale: string) => [...rulesKeys.catalog(), 'actions', locale] as const,
  automationSettings: () => [...rulesKeys.all, 'automation-settings'] as const,
  testMessages: (sampleSize: number) => [...rulesKeys.all, 'test-messages', sampleSize] as const,
} as const;
