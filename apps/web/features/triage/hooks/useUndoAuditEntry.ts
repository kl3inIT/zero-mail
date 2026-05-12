'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { billingKeys } from '@/features/billing/query-keys';
import { undoAuditEntry } from '@/features/triage/api/triage-api';
import { triageKeys } from '@/features/triage/query-keys';

export function useUndoAuditEntry() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (auditId: string) => undoAuditEntry(auditId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: triageKeys.auditLog() }),
        queryClient.invalidateQueries({ queryKey: billingKeys.balance() }),
      ]);
    },
  });
}
