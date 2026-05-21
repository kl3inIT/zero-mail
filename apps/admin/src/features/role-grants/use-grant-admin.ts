import { useMutation, useQueryClient } from '@tanstack/react-query';

import { grantAdmin } from './role-grants-api';
import { roleGrantQueryKeys } from './query-keys';

export function useGrantAdmin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: grantAdmin,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: roleGrantQueryKeys.admins });
    },
  });
}
