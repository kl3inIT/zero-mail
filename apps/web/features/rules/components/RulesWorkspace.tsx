'use client';

import { useEffect, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Plus, Sparkles } from 'lucide-react';

import { Alert, AlertDescription } from '@/components/ui/alert';
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
  const [composerDialogOpen, setComposerDialogOpen] = useState(false);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);

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

    const basePayload = {
      displayName: compileResult.compiled.displayName ?? fallbackDisplayName(sourceText),
      sourceText,
      compiled: compiledResponseToRequest(compileResult.compiled),
    };

    let savedRule;
    if (selectedRule?.ruleId && selectedRule.sourceText) {
      savedRule = await updateRuleMutation.mutateAsync({
        ruleId: selectedRule.ruleId,
        payload: {
          ...basePayload,
          entityVersion: selectedRule.entityVersion ?? 0,
        },
      });
    } else {
      savedRule = await createRuleMutation.mutateAsync(basePayload);
    }

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
      setTemplateDialogOpen(false);
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

  const canPreview = canPreviewRule(selectedRule, sourceText, compileResult);

  function handleNewRule() {
    setSelectedRuleId(null);
    setSourceText('');
    setCompileResult(null);
    setCompileError(null);
    setInsufficientCreditError(null);
    setClarificationAnswer('');
    setPreview(null);
    setPreviewError(null);
    setGmailUnavailableError(null);
    setComposerDialogOpen(true);
  }

  function handleEditRule(rule: RuleResponse) {
    selectRule(rule);
    setComposerDialogOpen(true);
  }

  return (
    <div className="space-y-6">
      <div className="grid items-start gap-6 lg:grid-cols-[380px_1fr]">
        {/* Left column: rule list + main action */}
        <div className="flex flex-col gap-4 lg:sticky lg:top-20">
          <Button
            type="button"
            size="lg"
            className="h-12 w-full gap-3 rounded-xl text-base font-semibold shadow-sm transition-all hover:shadow-md"
            onClick={handleNewRule}
          >
            <Plus className="size-5" />
            {t('rules.composer.newRuleCta')}
          </Button>

          <RuleList
            rules={rules}
            selectedRuleId={selectedRuleId}
            isLoading={rulesQuery.isLoading}
            pendingRuleId={pendingRuleId}
            canEnableRule={canEnableRule}
            onSelectRule={selectRule}
            onMoveRule={handleMoveRule}
            onEditRule={handleEditRule}
            onToggleEnabled={handleToggleRule}
            onDeleteRule={handleDeleteRule}
            action={
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-primary hover:bg-primary/5 h-8 gap-1.5 rounded-md px-2 font-medium"
                onClick={() => setTemplateDialogOpen(true)}
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
            preview={preview}
            previewError={previewError}
            gmailUnavailableError={gmailUnavailableError}
            isPreviewing={previewSavedRuleMutation.isPending || previewDraftRuleMutation.isPending}
            isToggling={updateEnabledMutation.isPending}
            canPreview={canPreview}
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

      {/* Composer dialog — for creating and editing rules */}
      <Dialog open={composerDialogOpen} onOpenChange={setComposerDialogOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader className="sr-only">
            <DialogTitle>{t('rules.composer.title')}</DialogTitle>
            <DialogDescription>{t('rules.page.safetyNote')}</DialogDescription>
          </DialogHeader>
          <RuleComposer
            sourceText={sourceText}
            clarificationAnswer={clarificationAnswer}
            compileResult={compileResult}
            compileError={compileError}
            insufficientCreditError={insufficientCreditError}
            isCompiling={compileMutation.isPending}
            isSaving={createRuleMutation.isPending || updateRuleMutation.isPending}
            canPreview={canPreview}
            onSourceTextChange={updateSourceText}
            onClarificationAnswerChange={setClarificationAnswer}
            onCompile={handleCompile}
            onAnswerClarification={handleAnswerClarification}
            onSaveDisabledRule={handleSaveDisabledRule}
            onOpenPreview={() => setComposerDialogOpen(false)}
          />
        </DialogContent>
      </Dialog>

      {/* Template gallery dialog */}
      <Dialog open={templateDialogOpen} onOpenChange={setTemplateDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('rules.templates.title')}</DialogTitle>
            <DialogDescription>{t('rules.templates.disabledByDefault')}</DialogDescription>
          </DialogHeader>
          <RuleTemplateGallery
            templates={templates}
            isLoading={templatesQuery.isLoading}
            pendingTemplateKey={pendingTemplateKey}
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
