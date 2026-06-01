'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { activateByok, type ByokResponse } from '@/features/ai/api/byok-api';
import { aiSettingsKeys } from '@/features/ai/query-keys';

type MutationContext = {
  previousByok?: ByokResponse | null;
};

export function useActivateByok() {
  const queryClient = useQueryClient();
  const t = useTranslations();

  return useMutation<ByokResponse, Error, boolean, MutationContext>({
    mutationFn: activateByok,
    meta: {
      successMessage: t('ai.toast.aiPreferenceSaved'),
      errorMessage: t('errors.ai.byok.no_model_picked'),
    },
    onMutate: async (active) => {
      await queryClient.cancelQueries({ queryKey: aiSettingsKeys.byok() });
      const previousByok = queryClient.getQueryData<ByokResponse | null>(aiSettingsKeys.byok());
      if (previousByok) {
        queryClient.setQueryData<ByokResponse>(aiSettingsKeys.byok(), {
          ...previousByok,
          active,
        });
      }
      return { previousByok };
    },
    onError: (_mutationError, _active, context) => {
      if (context?.previousByok !== undefined) {
        queryClient.setQueryData(aiSettingsKeys.byok(), context.previousByok);
      }
    },
    onSuccess: (savedByok) => {
      queryClient.setQueryData(aiSettingsKeys.byok(), savedByok);
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: aiSettingsKeys.byok() });
    },
  });
}
