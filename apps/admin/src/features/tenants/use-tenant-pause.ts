import { useMutation, useQueryClient } from '@tanstack/react-query';

import { tenantQueryKeys } from './query-keys';
import { pauseTenant } from './tenants-api';

export function useTenantPause() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: pauseTenant,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: tenantQueryKeys.all });
    },
  });
}
