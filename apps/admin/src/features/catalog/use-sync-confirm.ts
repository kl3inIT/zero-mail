import { useMutation, useQueryClient } from '@tanstack/react-query';

import { confirmCatalogSync } from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export function useSyncConfirm() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: confirmCatalogSync,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all });
    },
  });
}
