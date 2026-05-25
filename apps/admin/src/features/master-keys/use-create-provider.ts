import { useMutation, useQueryClient } from '@tanstack/react-query';

import { createProvider } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useCreateProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createProvider,
    onSuccess: (response) => {
      void queryClient.invalidateQueries({ queryKey: masterKeyQueryKeys.all });
      void queryClient.invalidateQueries({
        queryKey: masterKeyQueryKeys.detail(response.provider),
      });
      void queryClient.invalidateQueries({
        queryKey: masterKeyQueryKeys.keys(response.provider),
      });
    },
    meta: {
      successMessage: 'Đã tạo provider.',
      errorMessage: 'Không thể tạo provider.',
    },
  });
}
