import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  createCatalogModel,
  type CatalogModelVerificationResponse,
  type CreateCatalogModelInput,
  verifyCatalogModel,
} from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export type CreateModelResult = {
  verification: CatalogModelVerificationResponse;
};

export function useCreateModel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: CreateCatalogModelInput): Promise<CreateModelResult> => {
      await createCatalogModel(input);
      // Newly-added catalog rows start as UNTESTED; the router refuses UNTESTED/FAILED
      // models when admins assign tier defaults. Run the live /models probe right away
      // so the row lands as VERIFIED (happy path) — if it returns FAILED, the dialog
      // surfaces the provider error so the admin can correct the model id.
      const verification = await verifyCatalogModel(input.modelId);
      return { verification };
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: catalogQueryKeys.provider(variables.provider),
      });
    },
  });
}
