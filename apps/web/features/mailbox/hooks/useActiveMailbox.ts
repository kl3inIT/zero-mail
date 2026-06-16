'use client';

import { useQuery } from '@tanstack/react-query';

import { getActiveMailbox } from '@/features/mailbox/api/mailbox-api';
import { mailboxQueryKeys } from '@/features/mailbox/query-keys';

export function useActiveMailbox() {
  return useQuery({
    queryKey: mailboxQueryKeys.active(),
    queryFn: ({ signal }) => getActiveMailbox({ signal }),
  });
}
