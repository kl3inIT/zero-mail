'use client';

import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

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

type CandidateStatus = 'APPROVED' | 'UNSUBSCRIBED' | 'AUTO_ARCHIVED';

const SENDER_ACTION_BATCH_SIZE = 25;

export function useSenderAction(spec: DateRangeSpec) {
  const queryClient = useQueryClient();
  const t = useTranslations();
  const candidatesKey = unsubscribeCampaignKeys.candidatesPrefix(spec);

  return useMutation<
    CleanupSenderActionResponse,
    Error,
    SenderActionMutationVariables,
    MutationContext
  >({
    mutationFn: async (variables) => {
      if (variables.senderEmails.length === 0) {
        throw new Error('Sender list must not be empty');
      }

      const responses: CleanupSenderActionResponse[] = [];
      for (const senderEmails of chunkSenderEmails(variables.senderEmails)) {
        responses.push(
          await runSenderAction({
            action: variables.action,
            senderEmails,
            labelName: variables.labelName,
          }),
        );
      }
      return aggregateSenderActionResponses(responses);
    },
    meta: {
      successMessage: ({ variables }) =>
        t(actionSuccessKey(variables as SenderActionMutationVariables)),
      successDescription: ({ data }) => {
        const response = data as CleanupSenderActionResponse;
        return response.affectedMessageCount > 0
          ? t('cleanup.unsubscribe.action.mailAffected', {
              count: response.affectedMessageCount,
            })
          : undefined;
      },
      errorMessage: t('cleanup.unsubscribe.action.genericError'),
    },
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
                ? optimisticCandidate(candidate, nextStatus)
                : candidate,
            );
          },
        );
      }
      return { previous };
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: candidatesKey });
    },
    onError: (_error, _variables, context) => {
      if (context) {
        for (const [key, data] of context.previous) {
          queryClient.setQueryData(key, data);
        }
      }
    },
  });
}

function chunkSenderEmails(senderEmails: string[]): string[][] {
  const chunks: string[][] = [];
  for (
    let startIndex = 0;
    startIndex < senderEmails.length;
    startIndex += SENDER_ACTION_BATCH_SIZE
  ) {
    chunks.push(senderEmails.slice(startIndex, startIndex + SENDER_ACTION_BATCH_SIZE));
  }
  return chunks;
}

function aggregateSenderActionResponses(
  responses: CleanupSenderActionResponse[],
): CleanupSenderActionResponse {
  return responses.reduce(
    (total, response) => ({
      senderCount: total.senderCount + (response.senderCount ?? 0),
      affectedMessageCount: total.affectedMessageCount + (response.affectedMessageCount ?? 0),
      failedMessageCount: total.failedMessageCount + (response.failedMessageCount ?? 0),
    }),
    { senderCount: 0, affectedMessageCount: 0, failedMessageCount: 0 },
  );
}

function optimisticCandidate(
  candidate: UnsubscribeCandidateResponse,
  nextStatus: CandidateStatus | 'clear',
): UnsubscribeCandidateResponse {
  if (nextStatus === 'clear') {
    const candidateWithoutStatus = { ...candidate };
    delete candidateWithoutStatus.status;
    return candidateWithoutStatus;
  }
  return { ...candidate, status: nextStatus };
}

function optimisticNextStatus(action: CleanupSenderAction): CandidateStatus | 'clear' | 'noop' {
  switch (action) {
    case 'APPROVE':
      return 'APPROVED';
    case 'UNAPPROVE':
      return 'clear';
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
