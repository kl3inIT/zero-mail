import { useMutation, useQueryClient } from '@tanstack/react-query';

import { saveMasterKey } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useSaveMasterKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: saveMasterKey,
    onSuccess: async (_result, variables) => {
      await queryClient.invalidateQueries({ queryKey: masterKeyQueryKeys.all });
      await queryClient.invalidateQueries({ queryKey: masterKeyQueryKeys.detail(variables.provider) });
    },
  });
}
