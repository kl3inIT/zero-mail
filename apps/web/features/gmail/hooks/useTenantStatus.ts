'use client';

import { useQuery } from '@tanstack/react-query';

import { getTenantStatus } from '@/features/gmail/api/gmail-api';
import { gmailQueryKeys } from '@/features/gmail/query-keys';

export function useTenantStatus() {
  return useQuery({
    queryKey: gmailQueryKeys.status(),
    queryFn: ({ signal }) => getTenantStatus({ signal }),
  });
}
