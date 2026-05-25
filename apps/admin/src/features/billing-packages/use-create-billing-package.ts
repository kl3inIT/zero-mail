import { useMutation, useQueryClient } from '@tanstack/react-query';

import { createBillingPackage } from './billing-packages-api';
import { billingPackageQueryKeys } from './query-keys';

export function useCreateBillingPackage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createBillingPackage,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: billingPackageQueryKeys.list() });
    },
    meta: {
      successMessage: 'Đã tạo gói.',
      errorMessage: 'Không thể tạo gói.',
    },
  });
}
