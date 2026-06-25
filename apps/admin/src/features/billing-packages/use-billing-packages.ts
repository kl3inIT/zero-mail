import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  fetchBillingPackages,
  updateBillingFeatureCreditCost,
  updateBillingFeaturePermission,
} from './billing-packages-api';
import { billingPackageQueryKeys } from './query-keys';

export function useBillingPackages() {
  return useQuery({
    queryKey: billingPackageQueryKeys.overview(),
    queryFn: fetchBillingPackages,
    refetchOnWindowFocus: false,
    meta: {
      errorMessage: 'Không thể tải cấu hình gói dịch vụ.',
    },
  });
}

export function useUpdateBillingFeaturePermission() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateBillingFeaturePermission,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: billingPackageQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã cập nhật quyền gói dịch vụ.',
      errorMessage: 'Không thể cập nhật quyền gói dịch vụ.',
    },
  });
}

export function useUpdateBillingFeatureCreditCost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateBillingFeatureCreditCost,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: billingPackageQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã cập nhật giá credit chức năng.',
      errorMessage: 'Không thể cập nhật giá credit chức năng.',
    },
  });
}
