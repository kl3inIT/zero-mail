import { useMutation, useQueryClient } from '@tanstack/react-query';

import { activateBillingPackage } from './billing-packages-api';
import { billingPackageQueryKeys } from './query-keys';

export function useActivateBillingPackage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: activateBillingPackage,
    onSuccess: (updatedPackage) => {
      void queryClient.invalidateQueries({ queryKey: billingPackageQueryKeys.list() });
      void queryClient.invalidateQueries({
        queryKey: billingPackageQueryKeys.detail(updatedPackage.id),
      });
    },
    meta: {
      successMessage: 'Đã bật gói.',
      errorMessage: 'Không thể bật gói.',
    },
  });
}
