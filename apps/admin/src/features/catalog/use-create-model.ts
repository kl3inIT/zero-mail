import { useMutation, useQueryClient } from '@tanstack/react-query';

import { createCatalogModel } from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export function useCreateModel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createCatalogModel,
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: catalogQueryKeys.provider(variables.provider),
      });
    },
  });
}
