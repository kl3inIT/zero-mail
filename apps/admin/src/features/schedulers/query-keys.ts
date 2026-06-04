export const schedulerQueryKeys = {
  all: ['schedulers'] as const,
  list: () => [...schedulerQueryKeys.all, 'list'] as const,
};
