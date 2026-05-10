'use client';

import { useEffect, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { Alert, AlertDescription } from '@/components/ui/alert';
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

  // Locked decision D-C2: server-side GET /api/rules materializes
  // selected templates idempotently. The frontend does NOT POST
  // /api/rules/templates/materialize-selected here - the list query
  // is the single source of truth for template-derived rules.

  const rules = useMemo(
    () => [...(rulesQuery.data?.rules ?? [])].sort(compareRulesByOrder),
    [rulesQuery.data?.rules],
  );
  const templates = templatesQuery.data ?? rulesQuery.data?.templates ?? [];

  const [selectedRuleId, setSelectedRuleId] = useState<string | null>(null);
  const [sourceText, setSourceText] = useState('');
  const [clarificationAnswer, setClarificationAnswer] = useState('');
  const [compileResult, setCompileResult] = useState<RuleCompileResult | null>(null);
  const [compileError, setCompileError] = useState<string | null>(null);
  const [insufficientCreditError, setInsufficientCreditError] = useState<string | null>(null);
  const [preview, setPreview] = useState<RulePreviewResponse | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [gmailUnavailableError, setGmailUnavailableError] = useState<string | null>(null);
  const [sampleSize, setSampleSize] = useState<SampleSize>(25);
  const [lastPreviewedRule, setLastPreviewedRule] = useState<{
    ruleId: string;
    entityVersion?: number;
  } | null>(null);
  const [pendingRuleId, setPendingRuleId] = useState<string | null>(null);
  const [pendingTemplateKey, setPendingTemplateKey] = useState<string | null>(null);

  const selectedRule = rules.find((rule) => rule.ruleId === selectedRuleId) ?? null;

  useEffect(() => {
    if (!selectedRuleId && rules[0]?.ruleId) {
      selectRule(rules[0]);
    }
  }, [rules, selectedRuleId]);

  function updateSourceText(nextSourceText: string) {
    setSourceText(nextSourceText);
    setCompileResult(null);
    setCompileError(null);
    setInsufficientCreditError(null);
    setClarificationAnswer('');
  }

  function selectRule(rule: RuleResponse) {
    setSelectedRuleId(rule.ruleId ?? null);
    setSourceText(rule.sourceText ?? '');
    setCompileResult(compiledResultFromRule(rule));
    setClarificationAnswer('');
    setCompileError(null);
    setInsufficientCreditError(null);
    setPreview(null);
    setPreviewError(null);
    setGmailUnavailableError(null);
  }

  async function handleCompile() {
    await runCompile(undefined);
  }

  async function handleAnswerClarification() {
    if (!clarificationAnswer.trim()) return;
    await runCompile(clarificationAnswer.trim());
  }

  async function runCompile(answer: string | undefined) {
    setCompileError(null);
    setInsufficientCreditError(null);
    try {
      const result = await compileMutation.mutateAsync({
        sourceText,
        clarificationAnswer: answer,
        priorCompileContext:
          compileResult?.status === 'clarificationRequired'
            ? compileResult.priorCompileContext
            : undefined,
      });
      setCompileResult(result);
      if (result.status === 'compiled') setClarificationAnswer('');
      if (result.status === 'invalid') setCompileError(t('errors.rules.compile.invalid'));
    } catch (error) {
      if (isInsufficientCredit(error)) {
        setInsufficientCreditError(t('errors.rules.insufficientCredits'));
        return;
      }
      setCompileError(t('errors.rules.compile.invalid'));
    }
  }

  async function handleSaveDisabledRule() {
    if (compileResult?.status !== 'compiled') return;

    const payload = {
      displayName: compileResult.compiled.displayName ?? fallbackDisplayName(sourceText),
      sourceText,
      compiled: compiledResponseToRequest(compileResult.compiled),
    };

    const savedRule =
      selectedRule?.ruleId && selectedRule.sourceText
        ? await updateRuleMutation.mutateAsync({ ruleId: selectedRule.ruleId, payload })
        : await createRuleMutation.mutateAsync(payload);

    selectRule(savedRule);
    setLastPreviewedRule(null);
  }

  async function handlePreview() {
    setPreviewError(null);
    setGmailUnavailableError(null);

    try {
      const draftPreviewRequired = isDirtySelectedDraft(selectedRule, sourceText, compileResult);
      const result =
        selectedRule?.ruleId !== undefined && !draftPreviewRequired
          ? await previewSavedRuleMutation.mutateAsync({
              ruleId: selectedRule.ruleId,
              payload: { sampleSize },
            })
          : compileResult?.status === 'compiled'
            ? await previewDraftRuleMutation.mutateAsync({
                compiled: compiledResponseToRequest(compileResult.compiled),
                sampleSize,
              })
            : null;

      if (result) {
        setPreview(result);
        if (selectedRule?.ruleId && !draftPreviewRequired) {
          setLastPreviewedRule({
            ruleId: selectedRule.ruleId,
            entityVersion: selectedRule.entityVersion,
          });
        }
      }
    } catch (error) {
      if (isGmailUnavailable(error)) {
        setGmailUnavailableError(t('errors.rules.gmail.unavailable'));
        return;
      }
      setPreviewError(t('errors.rules.preview.generic'));
    }
  }

  async function handleToggleRule(rule: RuleResponse) {
    if (!rule.ruleId) return;
    setPendingRuleId(rule.ruleId);
    try {
      await updateEnabledMutation.mutateAsync({ ruleId: rule.ruleId, enabled: !rule.enabled });
    } finally {
      setPendingRuleId(null);
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
    if (selectedRuleId === rule.ruleId) {
      setSelectedRuleId(null);
      setSourceText('');
      setCompileResult(null);
      setPreview(null);
    }
  }

  async function handleUseTemplate(template: RuleTemplateResponse) {
    if (!template.templateKey) return;
    setPendingTemplateKey(template.templateKey);
    try {
      const result = await materializeTemplateMutation.mutateAsync(template.templateKey);
      const createdRule = result.createdRules?.[0];
      if (createdRule) selectRule(createdRule);
    } finally {
      setPendingTemplateKey(null);
    }
  }

  function canEnableRule(rule: RuleResponse): boolean {
    return (
      Boolean(rule.ruleId) &&
      (rule.lastPreviewedEntityVersion === rule.entityVersion ||
        (lastPreviewedRule?.ruleId === rule.ruleId &&
          lastPreviewedRule?.entityVersion === rule.entityVersion))
    );
  }

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <h1 className="text-xl font-semibold">{t('rules.page.title')}</h1>
        <p className="text-muted-foreground max-w-3xl text-sm">{t('rules.page.intro')}</p>
      </header>

      {rulesQuery.error && (
        <Alert variant="destructive">
          <AlertDescription>{t('errors.fallback')}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 lg:grid-cols-[minmax(17rem,22rem)_minmax(0,1fr)]">
        <div className="space-y-4 lg:order-1">
          <RuleList
            rules={rules}
            selectedRuleId={selectedRuleId}
            isLoading={rulesQuery.isLoading}
            pendingRuleId={pendingRuleId}
            canEnableRule={canEnableRule}
            onSelectRule={selectRule}
            onMoveRule={handleMoveRule}
            onEditRule={selectRule}
            onToggleEnabled={handleToggleRule}
            onDeleteRule={handleDeleteRule}
          />
          <RuleTemplateGallery
            templates={templates}
            isLoading={templatesQuery.isLoading}
            pendingTemplateKey={pendingTemplateKey}
            onUseTemplate={handleUseTemplate}
          />
        </div>

        <div className="space-y-4 lg:order-2">
          <RuleComposer
            sourceText={sourceText}
            clarificationAnswer={clarificationAnswer}
            compileResult={compileResult}
            compileError={compileError}
            insufficientCreditError={insufficientCreditError}
            isCompiling={compileMutation.isPending}
            isSaving={createRuleMutation.isPending || updateRuleMutation.isPending}
            onSourceTextChange={updateSourceText}
            onClarificationAnswerChange={setClarificationAnswer}
            onCompile={handleCompile}
            onAnswerClarification={handleAnswerClarification}
            onSaveDisabledRule={handleSaveDisabledRule}
          />
          <RulePreviewPanel
            selectedRule={selectedRule}
            preview={preview}
            previewError={previewError}
            gmailUnavailableError={gmailUnavailableError}
            isPreviewing={previewSavedRuleMutation.isPending || previewDraftRuleMutation.isPending}
            isToggling={updateEnabledMutation.isPending}
            canPreview={canPreviewRule(selectedRule, sourceText, compileResult)}
            canEnable={selectedRule ? canEnableRule(selectedRule) : false}
            sampleSize={sampleSize}
            onSampleSizeChange={setSampleSize}
            onPreview={handlePreview}
            onToggleEnabled={() => {
              if (selectedRule) void handleToggleRule(selectedRule);
            }}
          />
        </div>
      </div>
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
  const code = apiErrorCode(error);
  return code === 'error.billing.insufficient' || code === 'error.rules.insufficient_credits';
}

function isGmailUnavailable(error: unknown): boolean {
  return apiErrorCode(error) === 'error.rules.gmail.unavailable';
}
