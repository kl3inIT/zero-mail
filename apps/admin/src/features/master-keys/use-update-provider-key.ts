import { useMutation, useQueryClient } from '@tanstack/react-query';

import { updateProviderKey } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useUpdateProviderKey(provider: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateProviderKey,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: masterKeyQueryKeys.keys(provider),
      });
    },
    meta: {
      successMessage: 'Đã cập nhật key.',
      errorMessage: 'Không thể cập nhật key.',
    },
  });
}
