'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import {
  generateDraft,
  isDraftGenerationInFlight,
} from '@/features/needs-reply/api/needs-reply-api';
import { needsReplyKeys } from '@/features/needs-reply/query-keys';
import { triageKeys } from '@/features/triage/query-keys';

export function useGenerateDraft() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation({
    mutationFn: (gmailThreadId: string) => generateDraft(gmailThreadId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: needsReplyKeys.all }),
        queryClient.invalidateQueries({ queryKey: triageKeys.auditLog() }),
      ]);
      toast.success(t('needsReply.toast.draftSaved'));
    },
    onError: (error) => {
      if (isDraftGenerationInFlight(error)) return;
      toast.error(t('needsReply.toast.draftFailed'));
    },
  });
}
