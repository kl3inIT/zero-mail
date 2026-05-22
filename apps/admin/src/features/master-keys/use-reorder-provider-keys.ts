import { useMutation, useQueryClient } from '@tanstack/react-query';

import { reorderProviderKeys } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useReorderProviderKeys(provider: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: reorderProviderKeys,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: masterKeyQueryKeys.keys(provider),
      });
    },
    meta: {
      successMessage: 'Đã đổi thứ tự key.',
      errorMessage: 'Không thể đổi thứ tự key.',
    },
  });
}
