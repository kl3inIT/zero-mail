'use client';

import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import {
  runSenderAction,
  type CleanupSenderAction,
  type CleanupSenderActionRequest,
  type CleanupSenderActionResponse,
  type UnsubscribeCandidateResponse,
} from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import type { DateRangeSpec } from '@/features/cleanup/unsubscribe-campaign/date-range-spec';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

/** Client-only flag that drives which toast message fires; never sent to the backend. */
export type SenderActionToastIntent = 'block' | undefined;

export type SenderActionMutationVariables = CleanupSenderActionRequest & {
  toastIntent?: SenderActionToastIntent;
};

type OptimisticSnapshot = Array<[QueryKey, UnsubscribeCandidateResponse[] | undefined]>;
type MutationContext = { previous: OptimisticSnapshot };

type CandidateStatus = 'APPROVED' | 'UNSUBSCRIBED' | 'AUTO_ARCHIVED' | null;

export function useSenderAction(spec: DateRangeSpec, limit: number) {
  const queryClient = useQueryClient();
  const t = useTranslations();
  const candidatesKey = unsubscribeCampaignKeys.candidatesPrefix(spec);

  return useMutation<
    CleanupSenderActionResponse,
    Error,
    SenderActionMutationVariables,
    MutationContext
  >({
    mutationFn: ({ toastIntent: _toastIntent, ...request }) => runSenderAction(request),
    onMutate: async (variables) => {
      await queryClient.cancelQueries({ queryKey: candidatesKey });
      const previous = queryClient.getQueriesData<UnsubscribeCandidateResponse[]>({
        queryKey: candidatesKey,
      });
      const nextStatus = optimisticNextStatus(variables.action);
      if (nextStatus !== 'noop') {
        const targets = new Set(variables.senderEmails);
        queryClient.setQueriesData<UnsubscribeCandidateResponse[]>(
          { queryKey: candidatesKey },
          (current) => {
            if (!current) return current;
            return current.map((candidate) =>
              candidate.senderEmail && targets.has(candidate.senderEmail)
                ? { ...candidate, status: nextStatus as CandidateStatus }
                : candidate,
            );
          },
        );
      }
      return { previous };
    },
    onSuccess: (response, variables) => {
      void queryClient.invalidateQueries({ queryKey: candidatesKey });
      toast.success(t(actionSuccessKey(variables)), {
        description:
          response.affectedMessageCount > 0
            ? t('cleanup.unsubscribe.action.mailAffected', {
                count: response.affectedMessageCount,
              })
            : undefined,
      });
    },
    onError: (_error, _variables, context) => {
      if (context) {
        for (const [key, data] of context.previous) {
          queryClient.setQueryData(key, data);
        }
      }
      toast.error(t('cleanup.unsubscribe.action.genericError'));
    },
  });
}

function optimisticNextStatus(action: CleanupSenderAction): CandidateStatus | 'noop' {
  switch (action) {
    case 'APPROVE':
      return 'APPROVED';
    case 'UNAPPROVE':
      return null;
    case 'MARK_UNSUBSCRIBED':
      return 'UNSUBSCRIBED';
    case 'AUTO_ARCHIVE':
      return 'AUTO_ARCHIVED';
    case 'ARCHIVE':
    case 'DELETE':
    case 'LABEL_FUTURE':
      return 'noop';
  }
}

function actionSuccessKey(variables: SenderActionMutationVariables) {
  // Block intent runs the AUTO_ARCHIVE backend action but the user clicked "Chặn" — surface a
  // toast that matches their button click, not the internal action name.
  if (variables.toastIntent === 'block') {
    return 'cleanup.unsubscribe.action.blockOk';
  }
  switch (variables.action) {
    case 'APPROVE':
      return 'cleanup.unsubscribe.action.approveOk';
    case 'UNAPPROVE':
      return 'cleanup.unsubscribe.action.unapproveOk';
    case 'AUTO_ARCHIVE':
      return 'cleanup.unsubscribe.action.autoArchiveOk';
    case 'ARCHIVE':
      return 'cleanup.unsubscribe.action.archiveOk';
    case 'DELETE':
      return 'cleanup.unsubscribe.action.deleteOk';
    case 'LABEL_FUTURE':
      return 'cleanup.unsubscribe.action.labelFutureOk';
    case 'MARK_UNSUBSCRIBED':
      return 'cleanup.unsubscribe.action.markUnsubscribedOk';
  }
}
