import { useMutation, useQueryClient } from '@tanstack/react-query';

import { revokeProviderKey } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useRevokeProviderKey(provider: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: revokeProviderKey,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: masterKeyQueryKeys.keys(provider),
      });
      void queryClient.invalidateQueries({ queryKey: masterKeyQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã xoá key.',
      errorMessage: 'Không thể xoá key.',
    },
  });
}
