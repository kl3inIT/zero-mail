import { useMutation, useQueryClient } from '@tanstack/react-query';

import { rotateMasterKey } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useRotateMasterKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: rotateMasterKey,
    onSuccess: async (_result, variables) => {
      await queryClient.invalidateQueries({ queryKey: masterKeyQueryKeys.all });
      await queryClient.invalidateQueries({ queryKey: masterKeyQueryKeys.detail(variables.provider) });
    },
  });
}
