'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  compileRule,
  createRule,
  deleteRule,
  getRule,
  listRules,
  listRuleTemplates,
  materializeRuleTemplate,
  previewDraftRule,
  previewSavedRule,
  reorderRules,
  updateRule,
  updateRuleEnabled,
  type RuleCreateRequest,
  type RuleDraftPreviewRequest,
  type RuleListResponse,
  type RuleOrderEntryRequest,
  type RulePreviewRequest,
  type RuleReorderRequest,
  type RuleResponse,
  type RuleUpdateRequest,
} from '@/features/rules/api/rules-api';
import { rulesKeys } from '@/features/rules/query-keys';

export type ReorderRulesInput = {
  entries: RuleOrderEntryRequest[];
  orderedRules: RuleResponse[];
};

export function useRules() {
  return useQuery({ queryKey: rulesKeys.list(), queryFn: listRules });
}

export function useRule(ruleId: string | null | undefined) {
  return useQuery({
    queryKey: rulesKeys.detail(ruleId ?? 'missing-rule'),
    queryFn: () => getRule(ruleId ?? ''),
    enabled: Boolean(ruleId),
  });
}

export function useRuleTemplates() {
  return useQuery({ queryKey: rulesKeys.templates(), queryFn: listRuleTemplates });
}

export function useCompileRule() {
  return useMutation({ mutationFn: compileRule });
}

export function useCreateRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: RuleCreateRequest) => createRule(payload),
    onSuccess: async (rule) => {
      await queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
      if (rule.ruleId) {
        await queryClient.invalidateQueries({ queryKey: rulesKeys.detail(rule.ruleId) });
      }
    },
  });
}

export function useUpdateRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ ruleId, payload }: { ruleId: string; payload: RuleUpdateRequest }) =>
      updateRule(ruleId, payload),
    onSuccess: async (rule, variables) => {
      await queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
      await queryClient.invalidateQueries({
        queryKey: rulesKeys.detail(rule.ruleId ?? variables.ruleId),
      });
    },
  });
}

export function useUpdateRuleEnabled() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ ruleId, enabled }: { ruleId: string; enabled: boolean }) =>
      updateRuleEnabled(ruleId, { enabled }),
    onSuccess: async (rule, variables) => {
      await queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
      await queryClient.invalidateQueries({
        queryKey: rulesKeys.detail(rule.ruleId ?? variables.ruleId),
      });
    },
  });
}

export function useReorderRules() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ entries }: ReorderRulesInput) =>
      reorderRules({ entries } satisfies RuleReorderRequest),
    onMutate: async ({ orderedRules }) => {
      await queryClient.cancelQueries({ queryKey: rulesKeys.list() });
      const previousList = queryClient.getQueryData<RuleListResponse>(rulesKeys.list());

      queryClient.setQueryData<RuleListResponse>(rulesKeys.list(), (currentList) => {
        if (!currentList) return currentList;
        return {
          ...currentList,
          rules: orderedRules.map((rule, index) => ({ ...rule, orderIndex: index + 1 })),
        };
      });

      return { previousList };
    },
    onError: (_error, _variables, context) => {
      if (context?.previousList) {
        queryClient.setQueryData(rulesKeys.list(), context.previousList);
      }
    },
    onSuccess: (rules) => {
      for (const rule of rules) {
        if (rule.ruleId) queryClient.setQueryData(rulesKeys.detail(rule.ruleId), rule);
      }
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
    },
  });
}

export function useDeleteRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (ruleId: string) => deleteRule(ruleId),
    onSuccess: async (_data, ruleId) => {
      await queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
      await queryClient.invalidateQueries({ queryKey: rulesKeys.detail(ruleId) });
    },
  });
}

export function usePreviewSavedRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ ruleId, payload }: { ruleId: string; payload: RulePreviewRequest }) =>
      previewSavedRule(ruleId, payload),
    onSuccess: async (_preview, variables) => {
      await queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
      await queryClient.invalidateQueries({ queryKey: rulesKeys.detail(variables.ruleId) });
    },
  });
}

export function usePreviewDraftRule() {
  return useMutation({
    mutationFn: (payload: RuleDraftPreviewRequest) => previewDraftRule(payload),
  });
}

export function useMaterializeRuleTemplate() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (templateKey: string) => materializeRuleTemplate(templateKey),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
      await queryClient.invalidateQueries({ queryKey: rulesKeys.templates() });
    },
  });
}
