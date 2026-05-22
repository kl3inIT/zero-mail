import { useMutation, useQueryClient } from '@tanstack/react-query';

import { saveRuleCatalogExample, type SaveRuleCatalogExampleInput } from './rule-catalog-api';
import { ruleCatalogQueryKeys } from './query-keys';

export function useSaveExample() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SaveRuleCatalogExampleInput) => saveRuleCatalogExample(input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ruleCatalogQueryKeys.all });
    },
  });
}
