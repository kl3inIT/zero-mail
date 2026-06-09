import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  fetchFeedbackList,
  resolveFeedback,
  reopenFeedback,
  type FeedbackStatusFilter,
} from './feedback-api';

const FEEDBACK_QUERY_KEY = (status: FeedbackStatusFilter) => ['admin', 'feedback', status] as const;

export function useFeedbackList(status: FeedbackStatusFilter) {
  return useQuery({
    queryKey: FEEDBACK_QUERY_KEY(status),
    queryFn: () => fetchFeedbackList(status),
  });
}

export function useResolveFeedback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, adminNotes }: { id: string; adminNotes?: string }) =>
      resolveFeedback(id, adminNotes),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'feedback'] });
    },
    meta: {
      successMessage: 'Đã đánh dấu là đã xử lý.',
      errorMessage: 'Không thể cập nhật feedback.',
    },
  });
}

export function useReopenFeedback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => reopenFeedback(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'feedback'] });
    },
    meta: {
      successMessage: 'Đã mở lại.',
      errorMessage: 'Không thể cập nhật feedback.',
    },
  });
}
