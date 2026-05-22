import { useMutation, useQueryClient } from '@tanstack/react-query';

import { reorderRuleCatalog, type ReorderRuleCatalogInput } from './rule-catalog-api';
import { ruleCatalogQueryKeys } from './query-keys';

export function useReorderRuleCatalog() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ReorderRuleCatalogInput) => reorderRuleCatalog(input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ruleCatalogQueryKeys.all });
    },
  });
}
