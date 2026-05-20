import { useMutation, useQueryClient } from '@tanstack/react-query';

import { setCatalogDefault } from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export function useSetDefaultModel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: setCatalogDefault,
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: catalogQueryKeys.provider(variables.provider),
      });
    },
  });
}
