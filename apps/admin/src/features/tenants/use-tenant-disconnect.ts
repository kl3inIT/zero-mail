import { useMutation, useQueryClient } from '@tanstack/react-query';

import { tenantQueryKeys } from './query-keys';
import { disconnectTenant } from './tenants-api';

export function useTenantDisconnect() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: disconnectTenant,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: tenantQueryKeys.all });
    },
  });
}
