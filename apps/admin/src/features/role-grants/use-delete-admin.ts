import { useMutation, useQueryClient } from '@tanstack/react-query';

import { deleteAdmin } from './role-grants-api';
import { roleGrantQueryKeys } from './query-keys';

export function useDeleteAdmin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ adminUserId, reason }: { adminUserId: string; reason: string }) =>
      deleteAdmin(adminUserId, reason),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: roleGrantQueryKeys.admins });
    },
    meta: {
      successMessage: 'Đã xóa admin.',
      errorMessage: 'Không thể xóa admin.',
    },
  });
}
