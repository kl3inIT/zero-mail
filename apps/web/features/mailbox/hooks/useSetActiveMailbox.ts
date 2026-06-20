'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { analyticsKeys } from '@/features/analytics/query-keys';
import { inboxKeys } from '@/features/inbox/query-keys';
import { setActiveMailbox, type ActiveMailbox } from '@/features/mailbox/api/mailbox-api';
import { mailboxQueryKeys } from '@/features/mailbox/query-keys';
import { needsReplyKeys } from '@/features/needs-reply/query-keys';
import { rulesKeys } from '@/features/rules/query-keys';
import { triageKeys } from '@/features/triage/query-keys';

export function useSetActiveMailbox() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: setActiveMailbox,
    onSuccess: (activeMailbox: ActiveMailbox) => {
      queryClient.setQueryData(mailboxQueryKeys.active(), activeMailbox);
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: mailboxQueryKeys.all }),
        // Only refetch the inbox LIST, never the currently-open message detail/thread. The
        // selected message belongs to the PREVIOUS mailbox; invalidating inboxKeys.all would
        // force its detail query to refetch under the NEW active mailbox and 404. Once the list
        // refetches, the stale selection resolves to null and the detail query disables itself.
        queryClient.invalidateQueries({ queryKey: inboxKeys.pages() }),
        queryClient.invalidateQueries({ queryKey: needsReplyKeys.all }),
        queryClient.invalidateQueries({ queryKey: rulesKeys.all }),
        queryClient.invalidateQueries({ queryKey: triageKeys.all }),
        queryClient.invalidateQueries({ queryKey: analyticsKeys.all }),
      ]);
      window.location.reload();
    },
    meta: {
      successMessage: 'Active mailbox updated',
      errorMessage: 'Could not switch active mailbox',
    },
  });
}
