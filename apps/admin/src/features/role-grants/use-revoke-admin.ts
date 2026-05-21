import { useMutation, useQueryClient } from '@tanstack/react-query';

import { revokeAdmin } from './role-grants-api';
import { roleGrantQueryKeys } from './query-keys';

export function useRevokeAdmin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ adminUserId, reason }: { adminUserId: string; reason: string }) =>
      revokeAdmin(adminUserId, reason),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: roleGrantQueryKeys.admins });
    },
  });
}
