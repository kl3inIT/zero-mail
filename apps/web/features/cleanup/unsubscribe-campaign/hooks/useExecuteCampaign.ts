'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import {
  executeCampaign,
  type CampaignExecuteRequest,
  type CampaignExecuteResponse,
  type UnsubscribeCandidateResponse,
} from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import type { DateRangeSpec } from '@/features/cleanup/unsubscribe-campaign/date-range-spec';
import { unsubscribeCampaignKeys } from '@/features/cleanup/unsubscribe-campaign/query-keys';

function extractStatusAndCode(error: unknown): { status?: number; errorCode?: string } {
  if (error && typeof error === 'object') {
    const errorObject = error as { status?: unknown; code?: unknown; errorCode?: unknown };
    const status = typeof errorObject.status === 'number' ? errorObject.status : undefined;
    const errorCode =
      typeof errorObject.errorCode === 'string'
        ? errorObject.errorCode
        : typeof errorObject.code === 'string'
          ? errorObject.code
          : undefined;
    return { status, errorCode };
  }
  return {};
}

type ExecuteCampaignOptions = {
  onSuccess?: (executeResponse: CampaignExecuteResponse, variables: CampaignExecuteRequest) => void;
};

export function useExecuteCampaign(spec: DateRangeSpec, options: ExecuteCampaignOptions = {}) {
  const queryClient = useQueryClient();
  const t = useTranslations();

  type CandidateCache = UnsubscribeCandidateResponse[];
  type OptimisticContext = {
    snapshots: Array<[readonly unknown[], CandidateCache | undefined]>;
    // Sonner returns either string or number depending on the call shape; we just thread it
    // through so onSuccess/onError can update the same toast instead of stacking a new one.
    toastId: string | number;
  };
  const candidatesKeyPrefix = unsubscribeCampaignKeys.candidatesPrefix(spec);

  return useMutation<CampaignExecuteResponse, Error, CampaignExecuteRequest, OptimisticContext>({
    mutationFn: executeCampaign,
    // F1 — optimistic removal: drop the senders from every cached
    // candidates page for this window before the network call returns.
    // Backend will already exclude them (NOT EXISTS attempt PENDING/RUNNING)
    // on the next refetch; this just removes the round-trip blink where
    // the row sits there until refetch settles. If the call fails we
    // restore the snapshot in onError so the row reappears.
    onMutate: async (variables) => {
      await queryClient.cancelQueries({ queryKey: candidatesKeyPrefix });
      const senderEmailsToRemove = new Set(variables.senderEmails);
      const snapshots: OptimisticContext['snapshots'] = [];
      const matchingQueries = queryClient.getQueriesData<CandidateCache>({
        queryKey: candidatesKeyPrefix,
      });
      for (const [matchedKey, previousData] of matchingQueries) {
        snapshots.push([matchedKey, previousData]);
        if (!previousData) continue;
        queryClient.setQueryData<CandidateCache>(matchedKey, (current) =>
          current?.filter((candidate) => !senderEmailsToRemove.has(candidate.senderEmail ?? '')),
        );
      }
      // Fire an in-flight loading toast and thread its id through context so onSuccess /
      // onError can swap it in place instead of stacking. Without this, fast networks show no
      // feedback at all between click and result — and slow networks looked indistinguishable
      // from a frozen UI until the row finally disappeared.
      const toastId = toast.loading(t('cleanup.unsubscribe.action.submitting'), {
        description: t('cleanup.unsubscribe.action.submittingDescription', {
          count: variables.senderEmails.length,
        }),
      });
      return { snapshots, toastId };
    },
    onSuccess: (executeResponse, variables, context) => {
      // Reconcile with the server — refetch happens via invalidate; if the
      // worker has not flipped state yet the SQL filter excludes PENDING so
      // the row stays gone, matching the optimistic state.
      void queryClient.invalidateQueries({ queryKey: candidatesKeyPrefix });
      toast.success(t('cleanup.unsubscribe.action.submitOk'), {
        id: context?.toastId,
        description: t('cleanup.unsubscribe.action.submitDescription', {
          count: variables.senderEmails.length,
        }),
      });
      options.onSuccess?.(executeResponse, variables);
    },
    onError: (mutationError, _variables, context) => {
      // Roll back the optimistic removal so the user does not lose their
      // selection target. invalidateQueries fires after to repaint from
      // server truth in case the request actually succeeded server-side.
      if (context) {
        for (const [matchedKey, previousData] of context.snapshots) {
          queryClient.setQueryData<CandidateCache>(matchedKey, previousData);
        }
      }
      void queryClient.invalidateQueries({ queryKey: candidatesKeyPrefix });

      const { status, errorCode } = extractStatusAndCode(mutationError);
      const toastId = context?.toastId;
      if (status === 400 && errorCode === 'error.cleanup.campaign.too_many_senders') {
        toast.error(t('cleanup.unsubscribe.action.errCapSender'), { id: toastId });
      } else if (status === 400 && errorCode === 'error.cleanup.campaign.too_many_messages') {
        toast.error(t('cleanup.unsubscribe.action.errCapMessage'), { id: toastId });
      } else if (status === 409) {
        // 409 = SuppressedSenderException OR concurrent PENDING attempt — the candidate row the
        // user clicked has changed under them. Better message + invalidate forces a fresh fetch
        // so the next click reflects reality.
        toast.error(t('cleanup.unsubscribe.action.errConflict'), { id: toastId });
      } else {
        toast.error(t('cleanup.unsubscribe.action.errGeneric'), { id: toastId });
      }
    },
  });
}
