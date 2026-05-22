import { useMutation, useQueryClient } from '@tanstack/react-query';

import { tenantQueryKeys } from './query-keys';
import { deleteTenant } from './tenants-api';

export function useTenantDelete() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteTenant,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: tenantQueryKeys.all });
    },
  });
}
