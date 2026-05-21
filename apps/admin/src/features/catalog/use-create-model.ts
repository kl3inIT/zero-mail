import { useMutation, useQueryClient } from '@tanstack/react-query';

import {
  createCatalogModel,
  type CatalogModelVerificationResponse,
  type CreateCatalogModelInput,
  verifyCatalogModel,
} from './catalog-api';
import { catalogQueryKeys } from './query-keys';

export type CreateModelResult = {
  outcome: 'created' | 'already-exists';
  verification: CatalogModelVerificationResponse;
};

export function useCreateModel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: CreateCatalogModelInput): Promise<CreateModelResult> => {
      // Newly-inserted catalog rows start as UNTESTED, and the router refuses
      // UNTESTED/FAILED models when admins assign tier defaults. Chain the live
      // probe so the row lands as VERIFIED on the happy path. If a previous
      // attempt already inserted the row (failed verify → row still exists),
      // createCatalogModel returns 'already-exists' instead of throwing — we
      // re-run verify so the admin can retry without manually deleting first.
      const outcome = await createCatalogModel(input);
      const verification = await verifyCatalogModel(input.modelId);
      return { outcome, verification };
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: catalogQueryKeys.provider(variables.provider),
      });
    },
  });
}
