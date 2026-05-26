export const aiSettingsKeys = {
  all: ['ai-settings'] as const,
  voice: () => [...aiSettingsKeys.all, 'voice'] as const,
  behavior: () => [...aiSettingsKeys.all, 'behavior'] as const,
};
