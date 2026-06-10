'use client';

import { useQuery } from '@tanstack/react-query';

import { listMailboxes } from '@/features/mailbox/api/mailbox-api';
import { mailboxQueryKeys } from '@/features/mailbox/query-keys';

export function useMailboxList() {
  return useQuery({
    queryKey: mailboxQueryKeys.list(),
    queryFn: ({ signal }) => listMailboxes({ signal }),
  });
}
