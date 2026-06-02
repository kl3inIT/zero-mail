export const queueQueryKeys = {
  all: ['queue'] as const,
  health: () => [...queueQueryKeys.all, 'health'] as const,
  deadLetters: (cursor: string | null, limit: number) =>
    [...queueQueryKeys.all, 'dead-letters', cursor, limit] as const,
  jobs: (status: string | null, jobType: string | null, cursor: string | null, limit: number) =>
    [...queueQueryKeys.all, 'jobs', status, jobType, cursor, limit] as const,
  jobDetail: (jobId: string) => [...queueQueryKeys.all, 'job', jobId] as const,
};
