import { useMutation, useQueryClient } from '@tanstack/react-query';

import { addProviderKey } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useAddProviderKey(provider: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: addProviderKey,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: masterKeyQueryKeys.keys(provider),
      });
      void queryClient.invalidateQueries({ queryKey: masterKeyQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã thêm key mới.',
      errorMessage: 'Không thể thêm key.',
    },
  });
}
