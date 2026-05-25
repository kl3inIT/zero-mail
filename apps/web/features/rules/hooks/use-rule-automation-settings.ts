'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  getRuleAutomationSettings,
  updateRuleAutomationSettings,
  type RuleAutomationSettingsResponse,
} from '@/features/rules/api/rule-catalog-api';
import { rulesKeys } from '@/features/rules/query-keys';

export function useRuleAutomationSettings() {
  return useQuery({
    queryKey: rulesKeys.automationSettings(),
    queryFn: getRuleAutomationSettings,
  });
}

export function useUpdateRuleAutomationSettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (autoSendRulesEnabled: boolean) =>
      updateRuleAutomationSettings({ autoSendRulesEnabled }),
    onMutate: async (autoSendRulesEnabled) => {
      await queryClient.cancelQueries({ queryKey: rulesKeys.automationSettings() });
      const previous = queryClient.getQueryData<RuleAutomationSettingsResponse>(
        rulesKeys.automationSettings(),
      );
      queryClient.setQueryData<RuleAutomationSettingsResponse>(rulesKeys.automationSettings(), {
        autoSendRulesEnabled,
      });
      return { previous };
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(rulesKeys.automationSettings(), context.previous);
      }
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: rulesKeys.automationSettings() });
    },
  });
}
