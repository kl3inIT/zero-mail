'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { markResolved } from '@/features/needs-reply/api/needs-reply-api';
import { needsReplyKeys } from '@/features/needs-reply/query-keys';

export function useMarkResolved() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (gmailThreadId: string) => markResolved(gmailThreadId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: needsReplyKeys.all });
    },
  });
}
