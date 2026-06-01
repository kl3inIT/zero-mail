export const knowledgeKeys = {
  all: ['knowledge'] as const,
  list: () => [...knowledgeKeys.all, 'list'] as const,
};
