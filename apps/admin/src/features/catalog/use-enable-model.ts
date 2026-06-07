import { useMutation, useQueryClient } from '@tanstack/react-query';

import { enableCatalogModel, verifyCatalogModel } from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export function useEnableModel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (modelId: string) => {
      await enableCatalogModel({ modelId });
      // Re-probe immediately so the model lands as VERIFIED (not UNTESTED) after re-enable.
      return verifyCatalogModel(modelId);
    },
    meta: {
      successMessage: 'Model đã được kích hoạt lại.',
      errorMessage: 'Không thể kích hoạt lại model.',
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all });
    },
  });
}
