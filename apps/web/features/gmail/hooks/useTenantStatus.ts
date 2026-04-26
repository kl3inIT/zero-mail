'use client';

import { useQuery } from '@tanstack/react-query';

import { getTenantStatus } from '@/features/gmail/api/status';
import { gmailKeys } from '@/features/gmail/api/keys';

export function useTenantStatus() {
  return useQuery({
    queryKey: gmailKeys.status(),
    queryFn: ({ signal }) => getTenantStatus({ signal }),
  });
}
