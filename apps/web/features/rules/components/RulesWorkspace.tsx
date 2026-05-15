'use client';

import { useEffect, useMemo, useReducer } from 'react';
import { useTranslations } from 'next-intl';
import { Plus, Sparkles } from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ErrorCode } from '@/lib/api/error-codes';
import { RuleComposer } from '@/features/rules/components/RuleComposer';
import { RuleList } from '@/features/rules/components/RuleList';
import { RulePreviewPanel } from '@/features/rules/components/RulePreviewPanel';
import { RuleTemplateGallery } from '@/features/rules/components/RuleTemplateGallery';
import {
  compiledResponseToRequest,
  type RuleCompileResult,
  type RulePreviewResponse,
  type RuleResponse,
  type RuleTemplateResponse,
} from '@/features/rules/api/rules-api';
import {
  useCompileRule,
  useCreateRule,
  useDeleteRule,
  useMaterializeRuleTemplate,
  usePreviewDraftRule,
  usePreviewSavedRule,
  useReorderRules,
  useRuleTemplates,
  useRules,
  useUpdateRule,
  useUpdateRuleEnabled,
} from '@/features/rules/hooks/use-rules';

type SampleSize = 10 | 25 | 50;

type LastPreviewedRule = { ruleId: string; entityVersion?: number };

type RulesWorkspaceState = {
  selectedRuleId: string | null;
  sourceText: string;
  clarificationAnswer: string;
  compileResult: RuleCompileResult | null;
  compileError: string | null;
  insufficientCreditError: string | null;
  preview: RulePreviewResponse | null;
  previewError: string | null;
  gmailUnavailableError: string | null;
  sampleSize: SampleSize;
  lastPreviewedRule: LastPreviewedRule | null;
  pendingRuleId: string | null;
  pendingTemplateKey: string | null;
  composerDialogOpen: boolean;
  templateDialogOpen: boolean;
};

type RulesWorkspaceAction =
  | { type: 'sourceTextChanged'; sourceText: string }
  | { type: 'clarificationAnswerChanged'; clarificationAnswer: string }
  | { type: 'ruleSelected'; rule: RuleResponse }
  | { type: 'newRuleStarted' }
  | { type: 'editRuleStarted'; rule: RuleResponse }
  | { type: 'compileStarted' }
  | { type: 'compileSucceeded'; result: RuleCompileResult; invalidMessage?: string }
  | { type: 'compileFailed'; message: string }
  | { type: 'compileInsufficientCredit'; message: string }
  | { type: 'composerDialogToggled'; open: boolean }
  | { type: 'templateDialogToggled'; open: boolean }
  | { type: 'previewStarted' }
  | {
      type: 'previewSucceeded';
      preview: RulePreviewResponse;
      lastPreviewedRule: LastPreviewedRule | null;
    }
  | { type: 'previewFailed'; message: string }
  | { type: 'previewGmailUnavailable'; message: string }
  | { type: 'sampleSizeChanged'; sampleSize: SampleSize }
  | { type: 'ruleTogglePending'; ruleId: string }
  | { type: 'rulePendingCleared' }
  | { type: 'templateMaterializePending'; templateKey: string }
  | { type: 'templateMaterializeCleared' }
  | { type: 'ruleSavedAfterCompose'; savedRule: RuleResponse }
  | { type: 'selectedRuleDeleted' };

const initialState: RulesWorkspaceState = {
  selectedRuleId: null,
  sourceText: '',
  clarificationAnswer: '',
  compileResult: null,
  compileError: null,
  insufficientCreditError: null,
  preview: null,
  previewError: null,
  gmailUnavailableError: null,
  sampleSize: 25,
  lastPreviewedRule: null,
  pendingRuleId: null,
  pendingTemplateKey: null,
  composerDialogOpen: false,
  templateDialogOpen: false,
};

function rulesWorkspaceReducer(
  state: RulesWorkspaceState,
  action: RulesWorkspaceAction,
): RulesWorkspaceState {
  switch (action.type) {
    case 'sourceTextChanged':
      return {
        ...state,
        sourceText: action.sourceText,
        compileResult: null,
        compileError: null,
        insufficientCreditError: null,
        clarificationAnswer: '',
      };
    case 'clarificationAnswerChanged':
      return { ...state, clarificationAnswer: action.clarificationAnswer };
    case 'ruleSelected':
      return applyRuleSelection(state, action.rule);
    case 'newRuleStarted':
      return {
        ...resetForFreshComposition(state),
        composerDialogOpen: true,
      };
    case 'editRuleStarted':
      return {
        ...applyRuleSelection(state, action.rule),
        composerDialogOpen: true,
      };
    case 'compileStarted':
      return { ...state, compileError: null, insufficientCreditError: null };
    case 'compileSucceeded':
      return {
        ...state,
        compileResult: action.result,
        clarificationAnswer: action.result.status === 'compiled' ? '' : state.clarificationAnswer,
        compileError: action.invalidMessage ?? null,
      };
    case 'compileFailed':
      return { ...state, compileError: action.message };
    case 'compileInsufficientCredit':
      return { ...state, insufficientCreditError: action.message };
    case 'composerDialogToggled':
      return { ...state, composerDialogOpen: action.open };
    case 'templateDialogToggled':
      return { ...state, templateDialogOpen: action.open };
    case 'previewStarted':
      return { ...state, previewError: null, gmailUnavailableError: null };
    case 'previewSucceeded':
      return {
        ...state,
        preview: action.preview,
        lastPreviewedRule: action.lastPreviewedRule ?? state.lastPreviewedRule,
      };
    case 'previewFailed':
      return { ...state, previewError: action.message };
    case 'previewGmailUnavailable':
      return { ...state, gmailUnavailableError: action.message };
    case 'sampleSizeChanged':
      return { ...state, sampleSize: action.sampleSize };
    case 'ruleTogglePending':
      return { ...state, pendingRuleId: action.ruleId };
    case 'rulePendingCleared':
      return { ...state, pendingRuleId: null };
    case 'templateMaterializePending':
      return { ...state, pendingTemplateKey: action.templateKey };
    case 'templateMaterializeCleared':
      return { ...state, pendingTemplateKey: null };
    case 'ruleSavedAfterCompose':
      return {
        ...applyRuleSelection(state, action.savedRule),
        lastPreviewedRule: null,
      };
    case 'selectedRuleDeleted':
      return {
        ...state,
        selectedRuleId: null,
        sourceText: '',
        compileResult: null,
        preview: null,
      };
  }
}

function applyRuleSelection(state: RulesWorkspaceState, rule: RuleResponse): RulesWorkspaceState {
  return {
    ...state,
    selectedRuleId: rule.ruleId ?? null,
    sourceText: rule.sourceText ?? '',
    compileResult: compiledResultFromRule(rule),
    clarificationAnswer: '',
    compileError: null,
    insufficientCreditError: null,
    preview: null,
    previewError: null,
    gmailUnavailableError: null,
  };
}

function resetForFreshComposition(state: RulesWorkspaceState): RulesWorkspaceState {
  return {
    ...state,
    selectedRuleId: null,
    sourceText: '',
    compileResult: null,
    compileError: null,
    insufficientCreditError: null,
    clarificationAnswer: '',
    preview: null,
    previewError: null,
    gmailUnavailableError: null,
  };
}

export function RulesWorkspace() {
  const t = useTranslations();
  const rulesQuery = useRules();
  const templatesQuery = useRuleTemplates();
  const compileMutation = useCompileRule();
  const createRuleMutation = useCreateRule();
  const updateRuleMutation = useUpdateRule();
  const reorderRulesMutation = useReorderRules();
  const deleteRuleMutation = useDeleteRule();
  const previewSavedRuleMutation = usePreviewSavedRule();
  const previewDraftRuleMutation = usePreviewDraftRule();
  const updateEnabledMutation = useUpdateRuleEnabled();
  const materializeTemplateMutation = useMaterializeRuleTemplate();

  const [state, dispatch] = useReducer(rulesWorkspaceReducer, initialState);

  const rules = useMemo(
    () => [...(rulesQuery.data?.rules ?? [])].sort(compareRulesByOrder),
    [rulesQuery.data?.rules],
  );
  const templates = templatesQuery.data ?? rulesQuery.data?.templates ?? [];

  const selectedRule = rules.find((rule) => rule.ruleId === state.selectedRuleId) ?? null;

  useEffect(() => {
    if (!state.selectedRuleId && rules[0]?.ruleId) {
      dispatch({ type: 'ruleSelected', rule: rules[0] });
    }
  }, [rules, state.selectedRuleId]);

  async function runCompile(answer: string | undefined) {
    dispatch({ type: 'compileStarted' });
    try {
      const result = await compileMutation.mutateAsync({
        sourceText: state.sourceText,
        clarificationAnswer: answer,
        priorCompileContext:
          state.compileResult?.status === 'clarificationRequired'
            ? state.compileResult.priorCompileContext
            : undefined,
      });
      dispatch({
        type: 'compileSucceeded',
        result,
        invalidMessage: result.status === 'invalid' ? t('errors.rules.compile.invalid') : undefined,
      });
    } catch (error) {
      if (isInsufficientCredit(error)) {
        dispatch({
          type: 'compileInsufficientCredit',
          message: t('errors.rules.insufficientCredits'),
        });
        return;
      }
      dispatch({ type: 'compileFailed', message: t('errors.rules.compile.invalid') });
    }
  }

  async function handleCompile() {
    await runCompile(undefined);
  }

  async function handleAnswerClarification() {
    if (!state.clarificationAnswer.trim()) return;
    await runCompile(state.clarificationAnswer.trim());
  }

  async function handleSaveDisabledRule() {
    if (state.compileResult?.status !== 'compiled') return;

    const basePayload = {
      displayName:
        state.compileResult.compiled.displayName ?? fallbackDisplayName(state.sourceText),
      sourceText: state.sourceText,
      compiled: compiledResponseToRequest(state.compileResult.compiled),
    };

    const savedRule =
      selectedRule?.ruleId && selectedRule.sourceText
        ? await updateRuleMutation.mutateAsync({
            ruleId: selectedRule.ruleId,
            payload: {
              ...basePayload,
              entityVersion: selectedRule.entityVersion ?? 0,
            },
          })
        : await createRuleMutation.mutateAsync(basePayload);

    dispatch({ type: 'ruleSavedAfterCompose', savedRule });
  }

  async function handlePreview() {
    dispatch({ type: 'previewStarted' });

    try {
      const draftPreviewRequired = isDirtySelectedDraft(
        selectedRule,
        state.sourceText,
        state.compileResult,
      );
      const result =
        selectedRule?.ruleId !== undefined && !draftPreviewRequired
          ? await previewSavedRuleMutation.mutateAsync({
              ruleId: selectedRule.ruleId,
              payload: { sampleSize: state.sampleSize },
            })
          : state.compileResult?.status === 'compiled'
            ? await previewDraftRuleMutation.mutateAsync({
                compiled: compiledResponseToRequest(state.compileResult.compiled),
                sampleSize: state.sampleSize,
              })
            : null;

      if (result) {
        dispatch({
          type: 'previewSucceeded',
          preview: result,
          lastPreviewedRule:
            selectedRule?.ruleId && !draftPreviewRequired
              ? { ruleId: selectedRule.ruleId, entityVersion: selectedRule.entityVersion }
              : null,
        });
      }
    } catch (error) {
      if (isGmailUnavailable(error)) {
        dispatch({
          type: 'previewGmailUnavailable',
          message: t('errors.rules.gmail.unavailable'),
        });
        return;
      }
      dispatch({ type: 'previewFailed', message: t('errors.rules.preview.generic') });
    }
  }

  async function handleToggleRule(rule: RuleResponse) {
    if (!rule.ruleId) return;
    dispatch({ type: 'ruleTogglePending', ruleId: rule.ruleId });
    try {
      await updateEnabledMutation.mutateAsync({ ruleId: rule.ruleId, enabled: !rule.enabled });
    } finally {
      dispatch({ type: 'rulePendingCleared' });
    }
  }

  async function handleMoveRule(rule: RuleResponse, direction: 'up' | 'down') {
    if (!rule.ruleId) return;
    const currentIndex = rules.findIndex((candidate) => candidate.ruleId === rule.ruleId);
    const nextIndex = direction === 'up' ? currentIndex - 1 : currentIndex + 1;
    if (currentIndex < 0 || nextIndex < 0 || nextIndex >= rules.length) return;

    const orderedRules = [...rules];
    const [movedRule] = orderedRules.splice(currentIndex, 1);
    if (!movedRule) return;
    orderedRules.splice(nextIndex, 0, movedRule);

    await reorderRulesMutation.mutateAsync({
      orderedRules,
      entries: orderedRules
        .filter((orderedRule) => orderedRule.ruleId)
        .map((orderedRule) => ({
          ruleId: orderedRule.ruleId as string,
          entityVersion: orderedRule.entityVersion ?? 0,
        })),
    });
  }

  async function handleDeleteRule(rule: RuleResponse) {
    if (!rule.ruleId) return;
    await deleteRuleMutation.mutateAsync(rule.ruleId);
    if (state.selectedRuleId === rule.ruleId) {
      dispatch({ type: 'selectedRuleDeleted' });
    }
  }

  async function handleUseTemplate(template: RuleTemplateResponse) {
    if (!template.templateKey) return;
    dispatch({ type: 'templateMaterializePending', templateKey: template.templateKey });
    try {
      const result = await materializeTemplateMutation.mutateAsync(template.templateKey);
      const createdRule = result.createdRules?.[0];
      if (createdRule) dispatch({ type: 'ruleSelected', rule: createdRule });
      dispatch({ type: 'templateDialogToggled', open: false });
    } finally {
      dispatch({ type: 'templateMaterializeCleared' });
    }
  }

  function canEnableRule(rule: RuleResponse): boolean {
    return (
      Boolean(rule.ruleId) &&
      (rule.lastPreviewedEntityVersion === rule.entityVersion ||
        (state.lastPreviewedRule?.ruleId === rule.ruleId &&
          state.lastPreviewedRule?.entityVersion === rule.entityVersion))
    );
  }

  const canPreview = canPreviewRule(selectedRule, state.sourceText, state.compileResult);

  return (
    <div className="space-y-6">
      <div className="grid items-start gap-6 lg:grid-cols-[380px_1fr]">
        {/* Left column: rule list + main action */}
        <div className="flex flex-col gap-4 lg:sticky lg:top-20">
          <Button
            type="button"
            size="lg"
            className="h-12 w-full gap-3 rounded-xl text-base font-semibold shadow-sm transition-all hover:shadow-md"
            onClick={() => dispatch({ type: 'newRuleStarted' })}
          >
            <Plus className="size-5" />
            {t('rules.composer.newRuleCta')}
          </Button>

          <RuleList
            rules={rules}
            selectedRuleId={state.selectedRuleId}
            isLoading={rulesQuery.isLoading}
            pendingRuleId={state.pendingRuleId}
            canEnableRule={canEnableRule}
            onSelectRule={(rule) => dispatch({ type: 'ruleSelected', rule })}
            onMoveRule={handleMoveRule}
            onEditRule={(rule) => dispatch({ type: 'editRuleStarted', rule })}
            onToggleEnabled={handleToggleRule}
            onDeleteRule={handleDeleteRule}
            action={
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-primary hover:bg-primary/5 h-8 gap-1.5 rounded-md px-2 font-medium"
                onClick={() => dispatch({ type: 'templateDialogToggled', open: true })}
              >
                <Sparkles className="size-3.5" />
                {t('rules.templates.browseCta')}
              </Button>
            }
          />
        </div>

        {/* Right column: preview panel */}
        <div className="lg:order-2">
          <RulePreviewPanel
            selectedRule={selectedRule}
            preview={state.preview}
            previewError={state.previewError}
            gmailUnavailableError={state.gmailUnavailableError}
            isPreviewing={previewSavedRuleMutation.isPending || previewDraftRuleMutation.isPending}
            isToggling={updateEnabledMutation.isPending}
            canPreview={canPreview}
            canEnable={selectedRule ? canEnableRule(selectedRule) : false}
            sampleSize={state.sampleSize}
            onSampleSizeChange={(sampleSize) => dispatch({ type: 'sampleSizeChanged', sampleSize })}
            onPreview={handlePreview}
            onToggleEnabled={() => {
              if (selectedRule) void handleToggleRule(selectedRule);
            }}
          />
        </div>
      </div>

      {/* Composer dialog — for creating and editing rules */}
      <Dialog
        open={state.composerDialogOpen}
        onOpenChange={(open) => dispatch({ type: 'composerDialogToggled', open })}
      >
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader className="sr-only">
            <DialogTitle>{t('rules.composer.title')}</DialogTitle>
            <DialogDescription>{t('rules.page.safetyNote')}</DialogDescription>
          </DialogHeader>
          <RuleComposer
            sourceText={state.sourceText}
            clarificationAnswer={state.clarificationAnswer}
            compileResult={state.compileResult}
            compileError={state.compileError}
            insufficientCreditError={state.insufficientCreditError}
            isCompiling={compileMutation.isPending}
            isSaving={createRuleMutation.isPending || updateRuleMutation.isPending}
            canPreview={canPreview}
            onSourceTextChange={(sourceText) => dispatch({ type: 'sourceTextChanged', sourceText })}
            onClarificationAnswerChange={(clarificationAnswer) =>
              dispatch({ type: 'clarificationAnswerChanged', clarificationAnswer })
            }
            onCompile={handleCompile}
            onAnswerClarification={handleAnswerClarification}
            onSaveDisabledRule={handleSaveDisabledRule}
            onOpenPreview={() => dispatch({ type: 'composerDialogToggled', open: false })}
          />
        </DialogContent>
      </Dialog>

      {/* Template gallery dialog */}
      <Dialog
        open={state.templateDialogOpen}
        onOpenChange={(open) => dispatch({ type: 'templateDialogToggled', open })}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('rules.templates.title')}</DialogTitle>
            <DialogDescription>{t('rules.templates.disabledByDefault')}</DialogDescription>
          </DialogHeader>
          <RuleTemplateGallery
            templates={templates}
            isLoading={templatesQuery.isLoading}
            pendingTemplateKey={state.pendingTemplateKey}
            hideHeader
            onUseTemplate={handleUseTemplate}
          />
        </DialogContent>
      </Dialog>
    </div>
  );
}

function compareRulesByOrder(left: RuleResponse, right: RuleResponse): number {
  return (left.orderIndex ?? 0) - (right.orderIndex ?? 0);
}

function compiledResultFromRule(rule: RuleResponse): RuleCompileResult | null {
  if (!rule.matcherAst || !rule.actionIntents) return null;
  return {
    status: 'compiled',
    compiled: {
      status: 'compiled',
      displayName: rule.displayName,
      sourceLanguage: rule.sourceLanguage,
      schemaVersion: rule.schemaVersion,
      matcherAst: rule.matcherAst,
      actionIntents: rule.actionIntents,
    },
  };
}

function fallbackDisplayName(sourceText: string): string {
  return sourceText.trim().split(/\s+/).slice(0, 6).join(' ');
}

function canPreviewRule(
  selectedRule: RuleResponse | null,
  sourceText: string,
  compileResult: RuleCompileResult | null,
): boolean {
  if (!selectedRule?.ruleId) return compileResult?.status === 'compiled';
  if (isDirtySelectedDraft(selectedRule, sourceText, compileResult)) {
    return compileResult?.status === 'compiled';
  }
  return true;
}

function isDirtySelectedDraft(
  selectedRule: RuleResponse | null,
  sourceText: string,
  compileResult: RuleCompileResult | null,
): boolean {
  if (!selectedRule?.ruleId) return false;
  if ((selectedRule.sourceText ?? '') !== sourceText) return true;
  if (compileResult?.status !== 'compiled') return false;
  return (
    compileResult.compiled.matcherAst !== selectedRule.matcherAst ||
    compileResult.compiled.actionIntents !== selectedRule.actionIntents ||
    compileResult.compiled.schemaVersion !== selectedRule.schemaVersion
  );
}

function apiErrorCode(error: unknown): string | undefined {
  if (error !== null && typeof error === 'object') {
    const code = (error as { code?: unknown }).code;
    if (typeof code === 'string') return code;
  }
  return undefined;
}

function isInsufficientCredit(error: unknown): boolean {
  return apiErrorCode(error) === ErrorCode.BillingInsufficient;
}

function isGmailUnavailable(error: unknown): boolean {
  return apiErrorCode(error) === ErrorCode.RulesGmailUnavailable;
}
