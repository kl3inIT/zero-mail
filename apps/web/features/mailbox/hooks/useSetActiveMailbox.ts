'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { analyticsKeys } from '@/features/analytics/query-keys';
import { inboxKeys } from '@/features/inbox/query-keys';
import { setActiveMailbox } from '@/features/mailbox/api/mailbox-api';
import { mailboxQueryKeys } from '@/features/mailbox/query-keys';
import { needsReplyKeys } from '@/features/needs-reply/query-keys';
import { rulesKeys } from '@/features/rules/query-keys';
import { triageKeys } from '@/features/triage/query-keys';

export function useSetActiveMailbox() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: setActiveMailbox,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: mailboxQueryKeys.all }),
        queryClient.invalidateQueries({ queryKey: inboxKeys.all }),
        queryClient.invalidateQueries({ queryKey: needsReplyKeys.all }),
        queryClient.invalidateQueries({ queryKey: rulesKeys.all }),
        queryClient.invalidateQueries({ queryKey: triageKeys.all }),
        queryClient.invalidateQueries({ queryKey: analyticsKeys.all }),
      ]);
    },
    meta: {
      successMessage: 'Active mailbox updated',
      errorMessage: 'Could not switch active mailbox',
    },
  });
}
