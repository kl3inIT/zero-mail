import { useMutation, useQueryClient } from '@tanstack/react-query';

import { assignFeatureDefaultTier } from './feature-defaults-api';
import { featureDefaultQueryKeys } from './query-keys';

export function useAssignTier() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: assignFeatureDefaultTier,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: featureDefaultQueryKeys.all }),
    meta: {
      successMessage: 'Đã cập nhật routing tier.',
      errorMessage: 'Không thể cập nhật routing tier.',
    },
  });
}
