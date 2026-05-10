export const rulesKeys = {
  all: ['rules'] as const,
  list: () => [...rulesKeys.all, 'list'] as const,
  detail: (ruleId: string) => [...rulesKeys.all, 'detail', ruleId] as const,
  templates: () => [...rulesKeys.all, 'templates'] as const,
} as const;
