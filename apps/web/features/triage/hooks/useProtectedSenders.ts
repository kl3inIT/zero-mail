'use client';

import { useQuery } from '@tanstack/react-query';

import { getProtectedSenders } from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useProtectedSenders() {
  return useQuery({ queryKey: triageKeys.senderSafetyNet(), queryFn: getProtectedSenders });
}
