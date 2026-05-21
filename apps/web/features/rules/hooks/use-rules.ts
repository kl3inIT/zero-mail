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
  previewAllEnabledRules,
  previewCustomMail,
  previewDraftRule,
  previewSavedRule,
  updateRule,
  updateRuleEnabled,
  type RuleCreateRequest,
  type RuleCustomPreviewRequest,
  type RuleDraftPreviewRequest,
  type RuleEnabledPreviewRequest,
  type RuleListResponse,
  type RulePreviewRequest,
  type RuleUpdateRequest,
} from '@/features/rules/api/rules-api';
import { rulesKeys } from '@/features/rules/query-keys';

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

// Optimistic toggle: flip the `enabled` flag in the list cache immediately;
// roll back on error. Rule-list is the only place this state is rendered,
// so we only need to mutate that cache.
export function useUpdateRuleEnabled() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ ruleId, enabled }: { ruleId: string; enabled: boolean }) =>
      updateRuleEnabled(ruleId, { enabled }),
    onMutate: async ({ ruleId, enabled }) => {
      await queryClient.cancelQueries({ queryKey: rulesKeys.list() });
      const previous = queryClient.getQueryData<RuleListResponse>(rulesKeys.list());
      if (previous) {
        queryClient.setQueryData<RuleListResponse>(rulesKeys.list(), {
          ...previous,
          rules: previous.rules.map((rule) =>
            rule.ruleId === ruleId ? { ...rule, enabled } : rule,
          ),
        });
      }
      return { previous };
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(rulesKeys.list(), context.previous);
      }
    },
    onSettled: (_data, _error, variables) => {
      queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
      queryClient.invalidateQueries({ queryKey: rulesKeys.detail(variables.ruleId) });
    },
  });
}

// Optimistic delete: remove the rule from the list immediately; restore on error.
export function useDeleteRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (ruleId: string) => deleteRule(ruleId),
    onMutate: async (ruleId) => {
      await queryClient.cancelQueries({ queryKey: rulesKeys.list() });
      const previous = queryClient.getQueryData<RuleListResponse>(rulesKeys.list());
      if (previous) {
        queryClient.setQueryData<RuleListResponse>(rulesKeys.list(), {
          ...previous,
          rules: previous.rules.filter((rule) => rule.ruleId !== ruleId),
        });
      }
      return { previous };
    },
    onError: (_error, _ruleId, context) => {
      if (context?.previous) {
        queryClient.setQueryData(rulesKeys.list(), context.previous);
      }
    },
    onSettled: (_data, _error, ruleId) => {
      queryClient.invalidateQueries({ queryKey: rulesKeys.list() });
      queryClient.invalidateQueries({ queryKey: rulesKeys.detail(ruleId) });
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

export function usePreviewCustomMail() {
  return useMutation({
    mutationFn: (payload: RuleCustomPreviewRequest) => previewCustomMail(payload),
  });
}

export function usePreviewAllEnabledRules() {
  return useMutation({
    mutationFn: (payload: RuleEnabledPreviewRequest) => previewAllEnabledRules(payload),
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
