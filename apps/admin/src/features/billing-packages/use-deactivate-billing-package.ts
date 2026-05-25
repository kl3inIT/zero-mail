import { useMutation, useQueryClient } from '@tanstack/react-query';

import { deactivateBillingPackage } from './billing-packages-api';
import { billingPackageQueryKeys } from './query-keys';

export function useDeactivateBillingPackage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deactivateBillingPackage,
    onSuccess: (updatedPackage) => {
      void queryClient.invalidateQueries({ queryKey: billingPackageQueryKeys.list() });
      void queryClient.invalidateQueries({
        queryKey: billingPackageQueryKeys.detail(updatedPackage.id),
      });
    },
    meta: {
      successMessage: 'Đã tắt gói.',
      errorMessage: 'Không thể tắt gói.',
    },
  });
}
