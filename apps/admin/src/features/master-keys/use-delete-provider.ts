import { useMutation, useQueryClient } from '@tanstack/react-query';

import { catalogQueryKeys } from '@/features/catalog/query-keys';

import { deleteProvider } from './master-keys-api';
import { masterKeyQueryKeys } from './query-keys';

export function useDeleteProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteProvider,
    onSuccess: async (_data, provider) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: masterKeyQueryKeys.all }),
        queryClient.removeQueries({ queryKey: masterKeyQueryKeys.detail(provider) }),
        queryClient.removeQueries({ queryKey: masterKeyQueryKeys.keys(provider) }),
        queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all }),
      ]);
    },
  });
}
