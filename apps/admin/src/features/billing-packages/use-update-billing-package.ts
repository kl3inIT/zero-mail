import { useMutation, useQueryClient } from '@tanstack/react-query';

import { updateBillingPackage } from './billing-packages-api';
import { billingPackageQueryKeys } from './query-keys';

export function useUpdateBillingPackage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateBillingPackage,
    onSuccess: (updatedPackage) => {
      void queryClient.invalidateQueries({ queryKey: billingPackageQueryKeys.list() });
      void queryClient.invalidateQueries({
        queryKey: billingPackageQueryKeys.detail(updatedPackage.id),
      });
    },
    meta: {
      successMessage: 'Đã cập nhật gói.',
      errorMessage: 'Không thể cập nhật gói.',
    },
  });
}
