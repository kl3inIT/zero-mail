import { useMutation, useQueryClient } from '@tanstack/react-query';

import { requeueDeadLetter, type RequeueInput, type RequeueResult } from './queue-api';
import { queueQueryKeys } from './query-keys';

export function useRequeueDeadLetter() {
  const queryClient = useQueryClient();
  return useMutation<RequeueResult, Error, RequeueInput>({
    mutationFn: requeueDeadLetter,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queueQueryKeys.all });
    },
  });
}
