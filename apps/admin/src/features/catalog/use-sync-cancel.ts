import { useMutation, useQueryClient } from '@tanstack/react-query';

import { cancelCatalogSync } from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export function useSyncCancel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: cancelCatalogSync,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.sync() });
    },
  });
}
