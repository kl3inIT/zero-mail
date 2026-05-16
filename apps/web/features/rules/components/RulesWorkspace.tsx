'use client';

import { useEffect, useMemo, useReducer, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Plus, Sparkles } from 'lucide-react';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ErrorCode } from '@/lib/api/error-codes';
import { useLocalizedApiError, type ApiError } from '@/lib/api/errors';
import { CustomMailTester } from '@/features/rules/components/CustomMailTester';
import { RuleComposer } from '@/features/rules/components/RuleComposer';
import { RuleList } from '@/features/rules/components/RuleList';
import { RulePreviewPanel } from '@/features/rules/components/RulePreviewPanel';
import { RuleTemplateGallery } from '@/features/rules/components/RuleTemplateGallery';
import {
  compiledResponseToRequest,
  type RuleCompiledPayloadResponse,
  type RuleCompileResult,
  type RuleCustomPreviewResponse,
  type RulePreviewResponse,
  type RuleResponse,
  type RuleTemplateResponse,
} from '@/features/rules/api/rules-api';
import type { BuiltManualRule } from '@/features/rules/lib/rule-structure';
import {
  useCompileRule,
  useCreateRule,
  useDeleteRule,
  useMaterializeRuleTemplate,
  usePreviewCustomMail,
  usePreviewDraftRule,
  usePreviewSavedRule,
  useRuleTemplates,
  useRules,
  useUpdateRule,
  useUpdateRuleEnabled,
} from '@/features/rules/hooks/use-rules';

type SampleSize = 10 | 20;

type LastPreviewedRule = { ruleId: string; entityVersion?: number };

type CompileOptions = {
  keepCurrentSourceText?: boolean;
  // Override the user-facing message shown when the model returns
  // status=invalid. Useful for refine-style flows where the generic
  // "couldn't compile" copy reads as if the original draft is gone.
  invalidMessageOverride?: string;
  // Treat a clarificationRequired response as a soft failure: show the
  // invalid-message override (red banner) and keep the current draft,
  // instead of routing the user into the clarification answer flow.
  // Used by refine — refining a draft should never demand a new free-text
  // clarification answer; the user can just rephrase the edit.
  treatClarificationAsInvalid?: boolean;
  priorDraftJson?: string;
  editInstruction?: string;
};

type RulesWorkspaceState = {
  selectedRuleId: string | null;
  sourceText: string;
  clarificationAnswer: string;
  compileResult: RuleCompileResult | null;
  // The form is driven by the last *successful* compile snapshot — this is
  // independent of compileResult so clarification/invalid responses do not
  // wipe the manual draft the user is editing.
  lastCompiled: RuleCompiledPayloadResponse | null;
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
  | {
      type: 'compileSucceeded';
      // null = "the model responded, but we are choosing to surface only the
      // invalidMessage (e.g. refine flow swallowing clarificationRequired)".
      result: RuleCompileResult | null;
      invalidMessage?: string;
      sourceText?: string;
    }
  | { type: 'compileFailed'; message: string }
  | { type: 'compileInsufficientCredit'; message: string }
  | { type: 'saveStarted' }
  | { type: 'saveFailed'; message: string }
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
  lastCompiled: null,
  compileError: null,
  insufficientCreditError: null,
  preview: null,
  previewError: null,
  gmailUnavailableError: null,
  sampleSize: 10,
  lastPreviewedRule: null,
  pendingRuleId: null,
  pendingTemplateKey: null,
  composerDialogOpen: false,
  templateDialogOpen: false,
};

function composerSessionKey(state: RulesWorkspaceState): string {
  const scope = state.selectedRuleId ?? 'new';
  const compiled = state.lastCompiled;
  if (!compiled) return `${scope}:fresh`;
  return [
    scope,
    'compiled',
    compiled.displayName ?? '',
    compiled.matcherAst ?? '',
    compiled.actionIntents ?? '',
  ].join(':');
}

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
        sourceText: action.sourceText ?? state.sourceText,
        compileResult: action.result ?? state.compileResult,
        lastCompiled:
          action.result?.status === 'compiled' ? action.result.compiled : state.lastCompiled,
        clarificationAnswer: action.result?.status === 'compiled' ? '' : state.clarificationAnswer,
        compileError: action.invalidMessage ?? null,
      };
    case 'compileFailed':
      return { ...state, compileError: action.message };
    case 'compileInsufficientCredit':
      return { ...state, insufficientCreditError: action.message };
    case 'saveStarted':
      return { ...state, compileError: null };
    case 'saveFailed':
      return { ...state, compileError: action.message };
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
        composerDialogOpen: false,
        lastPreviewedRule: null,
      };
    case 'selectedRuleDeleted':
      return {
        ...state,
        selectedRuleId: null,
        sourceText: '',
        compileResult: null,
        lastCompiled: null,
        preview: null,
      };
  }
}

function applyRuleSelection(state: RulesWorkspaceState, rule: RuleResponse): RulesWorkspaceState {
  const compiledResult = compiledResultFromRule(rule);
  return {
    ...state,
    selectedRuleId: rule.ruleId ?? null,
    sourceText: rule.sourceText ?? '',
    compileResult: compiledResult,
    lastCompiled: compiledResult?.status === 'compiled' ? compiledResult.compiled : null,
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
    lastCompiled: null,
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
  const localizeApiError = useLocalizedApiError();
  const rulesQuery = useRules();
  const templatesQuery = useRuleTemplates();
  const compileMutation = useCompileRule();
  const createRuleMutation = useCreateRule();
  const updateRuleMutation = useUpdateRule();
  const deleteRuleMutation = useDeleteRule();
  const previewSavedRuleMutation = usePreviewSavedRule();
  const previewDraftRuleMutation = usePreviewDraftRule();
  const updateEnabledMutation = useUpdateRuleEnabled();
  const materializeTemplateMutation = useMaterializeRuleTemplate();
  const previewCustomMailMutation = usePreviewCustomMail();

  const [state, dispatch] = useReducer(rulesWorkspaceReducer, initialState);
  const [customMailResult, setCustomMailResult] = useState<RuleCustomPreviewResponse | null>(null);
  const [customMailError, setCustomMailError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'list' | 'test'>('list');

  const rules = useMemo(
    () => [...(rulesQuery.data?.rules ?? [])].sort(compareRulesByOrder),
    [rulesQuery.data?.rules],
  );
  const templates = templatesQuery.data ?? rulesQuery.data?.templates ?? [];

  const selectedRule = rules.find((rule) => rule.ruleId === state.selectedRuleId) ?? null;

  useEffect(() => {
    if (!state.composerDialogOpen && !state.selectedRuleId && rules[0]?.ruleId) {
      dispatch({ type: 'ruleSelected', rule: rules[0] });
    }
  }, [rules, state.composerDialogOpen, state.selectedRuleId]);

  async function runCompile(
    answer: string | undefined,
    sourceTextOverride?: string,
    options: CompileOptions = {},
  ) {
    dispatch({ type: 'compileStarted' });
    try {
      const result = await compileMutation.mutateAsync({
        sourceText: sourceTextOverride ?? state.sourceText,
        clarificationAnswer: answer,
        priorCompileContext:
          state.compileResult?.status === 'clarificationRequired'
            ? state.compileResult.priorCompileContext
            : undefined,
        priorDraftJson: options.priorDraftJson,
        editInstruction: options.editInstruction,
      });
      const treatAsInvalid =
        result.status === 'invalid' ||
        (options.treatClarificationAsInvalid && result.status === 'clarificationRequired');
      const invalidMessage = treatAsInvalid
        ? (options.invalidMessageOverride ?? t('errors.rules.compile.invalid'))
        : undefined;
      const effectiveResult =
        options.treatClarificationAsInvalid && result.status === 'clarificationRequired'
          ? null
          : result;
      dispatch({
        type: 'compileSucceeded',
        result: effectiveResult,
        sourceText: options.keepCurrentSourceText ? undefined : sourceTextOverride,
        invalidMessage,
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

  async function handleRefineManualRule(manualRule: BuiltManualRule, instruction: string) {
    const priorDraftJson = buildPriorDraftJson(manualRule);
    const refineSourceText = manualRule.sourceText.trim().length
      ? manualRule.sourceText
      : manualRule.displayName || 'rule';
    await runCompile(undefined, refineSourceText, {
      keepCurrentSourceText: true,
      invalidMessageOverride: t('errors.rules.refine.generic'),
      treatClarificationAsInvalid: true,
      priorDraftJson,
      editInstruction: instruction.trim(),
    });
  }

  async function saveCompiledRule(basePayload: {
    displayName: string;
    sourceText: string;
    compiled: ReturnType<typeof compiledResponseToRequest>;
  }) {
    dispatch({ type: 'saveStarted' });

    const duplicateRule = findDuplicateRule(
      rules,
      selectedRule?.ruleId ?? null,
      basePayload.compiled,
    );
    if (duplicateRule) {
      dispatch({ type: 'saveFailed', message: t('errors.rules.duplicate') });
      return;
    }

    try {
      const savedRule = selectedRule?.ruleId
        ? await updateRuleMutation.mutateAsync({
            ruleId: selectedRule.ruleId,
            payload: {
              ...basePayload,
              entityVersion: selectedRule.entityVersion ?? 0,
            },
          })
        : await createRuleMutation.mutateAsync(basePayload);

      dispatch({ type: 'ruleSavedAfterCompose', savedRule });
    } catch (error) {
      const apiError = maybeApiError(error);
      dispatch({
        type: 'saveFailed',
        message: apiError ? localizeApiError(apiError) : t('errors.rules.save.generic'),
      });
    }
  }

  async function handleSaveDisabledRule() {
    if (state.compileResult?.status !== 'compiled') return;

    await saveCompiledRule({
      displayName:
        state.compileResult.compiled.displayName ?? fallbackDisplayName(state.sourceText),
      sourceText: state.sourceText,
      compiled: compiledResponseToRequest(state.compileResult.compiled),
    });
  }

  async function handleSaveManualRule(manualRule: BuiltManualRule) {
    await saveCompiledRule({
      displayName: manualRule.displayName,
      sourceText: manualRule.sourceText,
      compiled: compiledResponseToRequest(manualRule.compiled),
    });
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

  async function handleRunCustomMailTest(input: { subject: string; body: string }) {
    setCustomMailError(null);
    try {
      const response = await previewCustomMailMutation.mutateAsync({
        subject: input.subject,
        body: input.body,
        ruleIds: null,
      });
      setCustomMailResult(response);
    } catch {
      setCustomMailError(t('errors.rules.testCustom.generic'));
    }
  }

  const enabledRulesCount = rules.filter((rule) => rule.enabled).length;
  const canPreview = canPreviewRule(selectedRule, state.sourceText, state.compileResult);

  return (
    <Tabs
      value={activeTab}
      onValueChange={(nextValue) => setActiveTab(nextValue as 'list' | 'test')}
      className="space-y-6"
    >
      <TabsList aria-label={t('rules.tabs.label')}>
        <TabsTrigger value="list">{t('rules.tabs.list')}</TabsTrigger>
        <TabsTrigger value="test">{t('rules.tabs.test')}</TabsTrigger>
      </TabsList>

      <TabsContent value="list" className="space-y-6">
        <RuleList
          rules={rules}
          selectedRuleId={state.selectedRuleId}
          isLoading={rulesQuery.isLoading}
          pendingRuleId={state.pendingRuleId}
          onSelectRule={(rule) => dispatch({ type: 'ruleSelected', rule })}
          onEditRule={(rule) => dispatch({ type: 'editRuleStarted', rule })}
          onToggleEnabled={handleToggleRule}
          onDeleteRule={handleDeleteRule}
          action={
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="gap-1.5 rounded-md"
                onClick={() => dispatch({ type: 'templateDialogToggled', open: true })}
              >
                <Sparkles className="size-3.5" />
                {t('rules.templates.browseCta')}
              </Button>
              <Button
                type="button"
                size="sm"
                className="gap-1.5 rounded-md"
                onClick={() => dispatch({ type: 'newRuleStarted' })}
              >
                <Plus className="size-3.5" />
                {t('rules.composer.newRuleCta')}
              </Button>
            </div>
          }
        />
      </TabsContent>

      <TabsContent value="test" className="space-y-6">
        <p className="text-muted-foreground text-sm">
          {t('rules.tabs.testIntro', { count: enabledRulesCount })}
        </p>
        <Tabs defaultValue="custom" className="space-y-4">
          <TabsList aria-label={t('rules.tabs.testModeLabel')}>
            <TabsTrigger value="custom" className="gap-2">
              {t('rules.tabs.testCustom')}
              <Badge variant="outline" className="h-5 px-1.5 text-[10px]">
                {t('rules.tabs.freeBadge')}
              </Badge>
            </TabsTrigger>
            <TabsTrigger value="gmail" className="gap-2">
              {t('rules.tabs.testGmail')}
              <Badge variant="outline" className="h-5 px-1.5 text-[10px]">
                {t('rules.tabs.creditBadge')}
              </Badge>
            </TabsTrigger>
          </TabsList>

          <TabsContent value="custom">
            <CustomMailTester
              selectedCount={0}
              isRunning={previewCustomMailMutation.isPending}
              result={customMailResult}
              resultError={customMailError}
              onClearSelection={() => undefined}
              onRunTest={handleRunCustomMailTest}
            />
          </TabsContent>

          <TabsContent value="gmail" className="space-y-3">
            <Alert variant="warning">
              <AlertTitle>{t('rules.tabs.gmailCreditWarningTitle')}</AlertTitle>
              <AlertDescription>{t('rules.tabs.gmailCreditWarningBody')}</AlertDescription>
            </Alert>
            <RulePreviewPanel
              selectedRule={selectedRule}
              preview={state.preview}
              previewError={state.previewError}
              gmailUnavailableError={state.gmailUnavailableError}
              isPreviewing={
                previewSavedRuleMutation.isPending || previewDraftRuleMutation.isPending
              }
              canPreview={canPreview}
              sampleSize={state.sampleSize}
              onSampleSizeChange={(sampleSize) =>
                dispatch({ type: 'sampleSizeChanged', sampleSize })
              }
              onPreview={handlePreview}
            />
          </TabsContent>
        </Tabs>
      </TabsContent>

      {/* Composer dialog — for creating and editing rules */}
      <Dialog
        open={state.composerDialogOpen}
        onOpenChange={(open) => dispatch({ type: 'composerDialogToggled', open })}
      >
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-4xl">
          <DialogHeader className="sr-only">
            <DialogTitle>{t('rules.composer.title')}</DialogTitle>
            <DialogDescription>{t('rules.page.safetyNote')}</DialogDescription>
          </DialogHeader>
          {state.composerDialogOpen && (
            <RuleComposer
              key={composerSessionKey(state)}
              sourceText={state.sourceText}
              initialDisplayName={selectedRule?.displayName}
              clarificationAnswer={state.clarificationAnswer}
              compileResult={state.compileResult}
              lastCompiled={state.lastCompiled}
              compileError={state.compileError}
              insufficientCreditError={state.insufficientCreditError}
              isCompiling={compileMutation.isPending}
              isSaving={createRuleMutation.isPending || updateRuleMutation.isPending}
              onSourceTextChange={(sourceText) =>
                dispatch({ type: 'sourceTextChanged', sourceText })
              }
              onClarificationAnswerChange={(clarificationAnswer) =>
                dispatch({ type: 'clarificationAnswerChanged', clarificationAnswer })
              }
              onCompile={handleCompile}
              onAnswerClarification={handleAnswerClarification}
              onSaveDisabledRule={handleSaveDisabledRule}
              onSaveManualRule={handleSaveManualRule}
              onRefineManualRule={handleRefineManualRule}
            />
          )}
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
    </Tabs>
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

function buildPriorDraftJson(manualRule: BuiltManualRule): string {
  const priorDraft = {
    displayName: manualRule.displayName,
    matcher: parseJsonOrRaw(manualRule.compiled.matcherAst),
    actionIntents: parseJsonOrRaw(manualRule.compiled.actionIntents),
  };
  const serialized = JSON.stringify(priorDraft);
  return serialized.length > 4000 ? serialized.slice(0, 4000) : serialized;
}

function parseJsonOrRaw(jsonText: string | null | undefined): unknown {
  if (!jsonText) return null;
  try {
    return JSON.parse(jsonText) as unknown;
  } catch {
    return jsonText;
  }
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

function maybeApiError(error: unknown): ApiError | undefined {
  if (
    error !== null &&
    typeof error === 'object' &&
    typeof (error as { code?: unknown }).code === 'string'
  ) {
    return error as ApiError;
  }
  return undefined;
}

function findDuplicateRule(
  rules: RuleResponse[],
  selectedRuleId: string | null,
  compiled: ReturnType<typeof compiledResponseToRequest>,
): RuleResponse | null {
  const targetDefinitionKey = ruleDefinitionKey(compiled.matcherAst, compiled.actionIntents);
  if (!targetDefinitionKey) return null;
  return (
    rules.find((rule) => {
      if (!rule.ruleId || rule.ruleId === selectedRuleId) return false;
      return ruleDefinitionKey(rule.matcherAst, rule.actionIntents) === targetDefinitionKey;
    }) ?? null
  );
}

function ruleDefinitionKey(
  matcherAst: string | null | undefined,
  actionIntents: string | null | undefined,
): string | null {
  if (!matcherAst || !actionIntents) return null;
  return `${canonicalJson(matcherAst)}|${canonicalJson(actionIntents)}`;
}

function canonicalJson(json: string): string {
  try {
    return JSON.stringify(sortJsonKeys(JSON.parse(json)));
  } catch {
    return json.trim();
  }
}

function sortJsonKeys(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(sortJsonKeys);
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .sort(([leftKey], [rightKey]) => leftKey.localeCompare(rightKey))
        .map(([key, childValue]) => [key, sortJsonKeys(childValue)]),
    );
  }
  return value;
}
