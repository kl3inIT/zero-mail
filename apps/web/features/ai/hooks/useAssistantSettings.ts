'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  AssistantSettingsApiError,
  getAssistantSettings,
  updateAssistantSettings,
  type AssistantSettings,
  type AssistantSettingsUpdateInput,
} from '@/features/ai/api/assistant-settings-api';
import { aiKeys } from '@/features/ai/query-keys';

export function useAssistantSettings() {
  return useQuery<AssistantSettings, AssistantSettingsApiError>({
    queryKey: aiKeys.assistantSettings(),
    queryFn: getAssistantSettings,
    staleTime: 60_000,
  });
}

export function useUpdateAssistantSettings() {
  const queryClient = useQueryClient();
  return useMutation<AssistantSettings, AssistantSettingsApiError, AssistantSettingsUpdateInput>({
    mutationFn: updateAssistantSettings,
    onSuccess: (settings) => {
      queryClient.setQueryData(aiKeys.assistantSettings(), settings);
    },
    // Toasts are owned by the calling component so it can use useTranslations() — see
    // AssistantWritingProfileCard in AiConfigPage.
    meta: { silent: true },
  });
}
