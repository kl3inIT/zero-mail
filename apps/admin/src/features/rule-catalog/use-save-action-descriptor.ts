import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  saveRuleCatalogActionDescriptor,
  setRuleCatalogEnabled,
  type SaveRuleCatalogActionDescriptorInput,
  type SetRuleCatalogEnabledInput,
} from './rule-catalog-api';
import { ruleCatalogQueryKeys } from './query-keys';

export function useSaveActionDescriptor() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SaveRuleCatalogActionDescriptorInput) =>
      saveRuleCatalogActionDescriptor(input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ruleCatalogQueryKeys.all });
    },
  });
}

export function useSetRuleCatalogEnabled() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SetRuleCatalogEnabledInput) => setRuleCatalogEnabled(input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ruleCatalogQueryKeys.all });
    },
  });
}
