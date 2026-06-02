import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  cancelJob,
  forceRetryJob,
  type JobActionInput,
  type RequeueResult,
} from './queue-api';
import { queueQueryKeys } from './query-keys';

/** Force-retries a FAILED job (resets attempts, re-arms it). Invalidates the whole queue tree. */
export function useForceRetryJob() {
  const queryClient = useQueryClient();
  return useMutation<RequeueResult, Error, JobActionInput>({
    mutationFn: forceRetryJob,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queueQueryKeys.all });
    },
  });
}

/** Cancels a PENDING or stuck-PROCESSING job (terminal CANCELLED). Invalidates the queue tree. */
export function useCancelJob() {
  const queryClient = useQueryClient();
  return useMutation<RequeueResult, Error, JobActionInput>({
    mutationFn: cancelJob,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queueQueryKeys.all });
    },
  });
}
