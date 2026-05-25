import { useMutation, useQueryClient } from '@tanstack/react-query';

import { rejectWaitlistEntry } from './waitlist-api';
import { waitlistQueryKeys } from './query-keys';

export function useRejectWaitlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (waitlistId: string) => rejectWaitlistEntry(waitlistId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: waitlistQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã từ chối đăng ký.',
      errorMessage: 'Không từ chối được đăng ký.',
    },
  });
}
