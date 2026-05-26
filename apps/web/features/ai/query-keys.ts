export const aiSettingsKeys = {
  all: ['ai-settings'] as const,
  voice: () => [...aiSettingsKeys.all, 'voice'] as const,
  behavior: () => [...aiSettingsKeys.all, 'behavior'] as const,
  byok: () => [...aiSettingsKeys.all, 'byok'] as const,
  cost: (window: string) => [...aiSettingsKeys.all, 'cost', window] as const,
};
