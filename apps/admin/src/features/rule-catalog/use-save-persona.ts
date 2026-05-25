import { useMutation, useQueryClient } from '@tanstack/react-query';

import { saveRuleCatalogPersona, type SaveRuleCatalogPersonaInput } from './rule-catalog-api';
import { ruleCatalogQueryKeys } from './query-keys';

export function useSavePersona() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SaveRuleCatalogPersonaInput) => saveRuleCatalogPersona(input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ruleCatalogQueryKeys.all });
    },
  });
}
