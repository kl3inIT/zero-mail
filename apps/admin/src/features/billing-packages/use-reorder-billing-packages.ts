import { useMutation, useQueryClient } from '@tanstack/react-query';

import { reorderBillingPackages } from './billing-packages-api';
import { billingPackageQueryKeys } from './query-keys';

export function useReorderBillingPackages() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: reorderBillingPackages,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: billingPackageQueryKeys.list() });
    },
    meta: {
      successMessage: 'Đã lưu thứ tự gói.',
      errorMessage: 'Không thể lưu thứ tự gói.',
    },
  });
}
