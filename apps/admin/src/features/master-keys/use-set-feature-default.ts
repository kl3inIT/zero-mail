import { useMutation, useQueryClient } from '@tanstack/react-query';

import { setFeatureDefault } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useSetFeatureDefault() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: setFeatureDefault,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: masterKeyQueryKeys.all });
    },
  });
}
