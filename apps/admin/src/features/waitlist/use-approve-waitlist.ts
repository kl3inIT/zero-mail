import { useMutation, useQueryClient } from '@tanstack/react-query';

import { approveWaitlistEntry } from './waitlist-api';
import { waitlistQueryKeys } from './query-keys';

export function useApproveWaitlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (waitlistId: string) => approveWaitlistEntry(waitlistId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: waitlistQueryKeys.all });
    },
    meta: {
      successMessage: 'Đã duyệt. Email mời sẽ được gửi trong vòng 1 phút.',
      errorMessage: 'Không duyệt được đăng ký.',
    },
  });
}
