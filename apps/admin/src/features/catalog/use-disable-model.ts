import { useMutation, useQueryClient } from '@tanstack/react-query';

import { disableCatalogModel } from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export function useDisableModel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: disableCatalogModel,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all });
    },
  });
}
