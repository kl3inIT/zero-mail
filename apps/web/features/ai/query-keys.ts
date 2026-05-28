export const aiKeys = {
  all: ['ai'] as const,
  assistantSettings: () => [...aiKeys.all, 'assistant-settings'] as const,
};
