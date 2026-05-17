'use client';

import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';
import {
  AlertCircle,
  Archive,
  CheckCircle2,
  FileText,
  HelpCircle,
  Loader2,
  Plus,
  Save,
  Tags,
  Trash2,
  Wand2,
  Zap,
} from 'lucide-react';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import type {
  RuleCompiledPayloadResponse,
  RuleCompileResult,
} from '@/features/rules/api/rules-api';
import {
  actionRequiresValue,
  buildManualRule,
  conditionRequiresValue,
  describeAction,
  describeCondition,
  emptyAction,
  emptyCondition,
  MANUAL_ACTION_TYPES,
  MANUAL_CONDITION_TYPES,
  manualDraftFromCompiledRule,
  summarizeActionIntents,
  summarizeMatcherAst,
  type BuiltManualRule,
  type ManualAction,
  type ManualActionType,
  type ManualCondition,
  type ManualConditionType,
  type ManualMatchOperator,
  type ManualRuleDraft,
} from '@/features/rules/lib/rule-structure';
import { createRuleStructureCopy } from '@/features/rules/lib/rule-structure-copy';

type Props = {
  sourceText: string;
  initialDisplayName?: string | null;
  clarificationAnswer: string;
  compileResult: RuleCompileResult | null;
  // Sticky snapshot of the last *successfully compiled* draft. Drives the
  // manual form so clarification/invalid responses do not wipe edits.
  lastCompiled: RuleCompiledPayloadResponse | null;
  compileError: string | null;
  insufficientCreditError: string | null;
  isCompiling: boolean;
  isSaving: boolean;
  onSourceTextChange: (sourceText: string) => void;
  onClarificationAnswerChange: (answer: string) => void;
  onCompile: () => void;
  onAnswerClarification: () => void;
  onSaveDisabledRule: () => void;
  onSaveManualRule: (rule: BuiltManualRule) => void | Promise<void>;
  onRefineManualRule: (rule: BuiltManualRule, instruction: string) => void | Promise<void>;
};

export function RuleComposer({
  sourceText,
  initialDisplayName,
  clarificationAnswer,
  compileResult,
  lastCompiled,
  compileError,
  insufficientCreditError,
  isCompiling,
  isSaving,
  onSourceTextChange,
  onClarificationAnswerChange,
  onCompile,
  onAnswerClarification,
  onSaveDisabledRule,
  onSaveManualRule,
  onRefineManualRule,
}: Props) {
  const t = useTranslations();
  const structureCopy = createRuleStructureCopy(t as unknown as (key: string) => string);
  const compiled = lastCompiled;
  const [activeTab, setActiveTab] = useState<'describe' | 'manual'>(
    lastCompiled ? 'manual' : 'describe',
  );
  const compiledSignature = `${initialDisplayName ?? ''}|${compiled?.matcherAst ?? ''}|${
    compiled?.actionIntents ?? ''
  }`;
  const compiledManualDraft = manualDraftFromCompiledRule({
    displayName: initialDisplayName ?? compiled?.displayName,
    matcherAst: compiled?.matcherAst,
    actionIntents: compiled?.actionIntents,
  });
  const [manualDraftState, setManualDraftState] = useState<{
    signature: string;
    draft: ManualRuleDraft;
  }>(() => ({
    signature: compiledSignature,
    draft: compiledManualDraft,
  }));
  const manualDraft =
    manualDraftState.signature === compiledSignature ? manualDraftState.draft : compiledManualDraft;

  function setManualDraft(
    nextDraft: ManualRuleDraft | ((current: ManualRuleDraft) => ManualRuleDraft),
  ) {
    setManualDraftState((current) => {
      const currentDraft =
        current.signature === compiledSignature ? current.draft : compiledManualDraft;
      const draft = typeof nextDraft === 'function' ? nextDraft(currentDraft) : nextDraft;
      return { signature: compiledSignature, draft };
    });
  }

  const manualBuild = useMemo(() => buildManualRule(manualDraft), [manualDraft]);
  const hasSourceText = sourceText.trim().length > 0;
  const clarification =
    compileResult?.status === 'clarificationRequired' ? compileResult.clarification : null;
  const invalid = compileResult?.status === 'invalid' ? compileResult.invalid : null;
  const matcherReview = summarizeMatcherAst(
    compiled?.matcherAst,
    t('rules.composer.matcherReview'),
    structureCopy,
  );
  const actionReview = summarizeActionIntents(
    compiled?.actionIntents,
    t('rules.composer.actionReview'),
    structureCopy,
  );
  const exampleRules = [
    t('rules.composer.example.receipts'),
    t('rules.composer.example.calendar'),
    t('rules.composer.example.newsletters'),
  ];

  function updateCondition(conditionId: string, patch: Partial<ManualCondition>) {
    setManualDraft((current) => ({
      ...current,
      conditions: current.conditions.map((condition) =>
        condition.id === conditionId ? { ...condition, ...patch } : condition,
      ),
    }));
  }

  function updateAction(actionId: string, patch: Partial<ManualAction>) {
    setManualDraft((current) => ({
      ...current,
      actions: current.actions.map((action) =>
        action.id === actionId ? { ...action, ...patch } : action,
      ),
    }));
  }

  function removeCondition(conditionId: string) {
    setManualDraft((current) => ({
      ...current,
      conditions:
        current.conditions.length > 1
          ? current.conditions.filter((condition) => condition.id !== conditionId)
          : current.conditions,
    }));
  }

  function removeAction(actionId: string) {
    setManualDraft((current) => ({
      ...current,
      actions:
        current.actions.length > 1
          ? current.actions.filter((action) => action.id !== actionId)
          : current.actions,
    }));
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold tracking-tight">{t('rules.composer.title')}</h2>
        <p className="text-muted-foreground mt-1 text-sm">{t('rules.page.safetyNote')}</p>
      </div>

      <Tabs
        value={activeTab}
        onValueChange={(value) => setActiveTab(value as 'describe' | 'manual')}
      >
        <TabsList variant="line" aria-label={t('rules.composer.tabsLabel')}>
          <TabsTrigger value="describe" className="gap-2 px-3">
            <Wand2 className="size-4" aria-hidden="true" />
            {t('rules.composer.tab.describe')}
          </TabsTrigger>
          <TabsTrigger value="manual" className="gap-2 px-3">
            <FileText className="size-4" aria-hidden="true" />
            {t('rules.composer.tab.manual')}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="describe" className="mt-5 space-y-4">
          <div className="bg-background rounded-lg border p-4">
            <div className="space-y-2">
              <Label htmlFor="rules-source-text">{t('rules.composer.sourceLabel')}</Label>
              <Textarea
                id="rules-source-text"
                aria-label={t('rules.composer.sourceLabel')}
                value={sourceText}
                placeholder={t('rules.composer.sourcePlaceholder')}
                disabled={isCompiling}
                className="min-h-32 resize-y"
                onChange={(event) => onSourceTextChange(event.currentTarget.value)}
              />
              <div className="space-y-2">
                <p className="text-foreground text-xs font-medium">
                  {t('rules.composer.examplesHint')}
                </p>
                <div className="grid gap-2 sm:grid-cols-3">
                  {exampleRules.map((exampleRule) => (
                    <button
                      key={exampleRule}
                      type="button"
                      disabled={isCompiling}
                      onClick={() => onSourceTextChange(exampleRule)}
                      className="group border-primary/30 bg-primary/5 text-foreground hover:border-primary hover:bg-primary/10 focus-visible:ring-primary/60 flex items-start gap-2 rounded-md border px-3 py-2 text-left text-xs leading-relaxed whitespace-normal shadow-sm transition-colors hover:cursor-pointer focus-visible:ring-2 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      <Wand2 className="text-primary mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                      <span className="flex-1">{exampleRule}</span>
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {clarification?.question && (
            <Alert className="border-warning/40 bg-warning-soft/50 text-warning">
              <HelpCircle className="size-4" aria-hidden="true" />
              <AlertTitle>{clarification.question}</AlertTitle>
              <AlertDescription className="space-y-3 pt-2">
                <Label htmlFor="rules-clarification-answer">
                  {t('rules.composer.answerLabel')}
                </Label>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Input
                    id="rules-clarification-answer"
                    value={clarificationAnswer}
                    aria-label={t('rules.composer.answerLabel')}
                    disabled={isCompiling}
                    onChange={(event) => onClarificationAnswerChange(event.currentTarget.value)}
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={!clarificationAnswer.trim() || isCompiling}
                    onClick={onAnswerClarification}
                  >
                    {isCompiling && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
                    {t('rules.composer.answerClarification')}
                  </Button>
                </div>
              </AlertDescription>
            </Alert>
          )}

          {compiled && (
            <CompiledReview
              matcherReview={matcherReview}
              actionReview={actionReview}
              sourceLanguage={compiled.sourceLanguage}
            />
          )}

          <ComposerErrors
            insufficientCreditError={insufficientCreditError}
            compileError={compileError}
            invalidReason={invalid?.reason}
          />

          <div className="flex flex-col items-stretch gap-2 border-t pt-4 sm:flex-row">
            <Button type="button" disabled={!hasSourceText || isCompiling} onClick={onCompile}>
              {isCompiling ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <Wand2 className="size-4" aria-hidden="true" />
              )}
              {isCompiling ? t('rules.composer.compiling') : t('rules.composer.compileCta')}
            </Button>
            <Button
              type="button"
              variant="secondary"
              disabled={!compiled || isSaving || isCompiling}
              className="sm:ml-auto"
              onClick={onSaveDisabledRule}
            >
              {isSaving ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <Save className="size-4" aria-hidden="true" />
              )}
              {isSaving ? t('rules.composer.saving') : t('rules.composer.saveDisabledCta')}
            </Button>
          </div>
        </TabsContent>

        <TabsContent value="manual" className="mt-5 space-y-4">
          <ManualBuilder
            draft={manualDraft}
            builtRule={manualBuild}
            isCompiling={isCompiling}
            isSaving={isSaving}
            insufficientCreditError={insufficientCreditError}
            compileError={compileError}
            invalidReason={invalid?.reason}
            clarificationQuestion={clarification?.question ?? null}
            onDraftChange={setManualDraft}
            onUpdateCondition={updateCondition}
            onRemoveCondition={removeCondition}
            onUpdateAction={updateAction}
            onRemoveAction={removeAction}
            onSaveManualRule={onSaveManualRule}
            onRefineManualRule={onRefineManualRule}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function ManualBuilder({
  draft,
  builtRule,
  isCompiling,
  isSaving,
  insufficientCreditError,
  compileError,
  invalidReason,
  clarificationQuestion,
  onDraftChange,
  onUpdateCondition,
  onRemoveCondition,
  onUpdateAction,
  onRemoveAction,
  onSaveManualRule,
  onRefineManualRule,
}: {
  draft: ManualRuleDraft;
  builtRule: BuiltManualRule | null;
  isCompiling: boolean;
  isSaving: boolean;
  insufficientCreditError: string | null;
  compileError: string | null;
  invalidReason: string | undefined;
  clarificationQuestion: string | null;
  onDraftChange: (draft: ManualRuleDraft) => void;
  onUpdateCondition: (conditionId: string, patch: Partial<ManualCondition>) => void;
  onRemoveCondition: (conditionId: string) => void;
  onUpdateAction: (actionId: string, patch: Partial<ManualAction>) => void;
  onRemoveAction: (actionId: string) => void;
  onSaveManualRule: (rule: BuiltManualRule) => void | Promise<void>;
  onRefineManualRule: (rule: BuiltManualRule, instruction: string) => void | Promise<void>;
}) {
  const t = useTranslations();
  const structureCopy = createRuleStructureCopy(t as unknown as (key: string) => string);
  const [refinementInstruction, setRefinementInstruction] = useState('');

  return (
    <div className="space-y-4">
      <ComposerErrors
        insufficientCreditError={insufficientCreditError}
        compileError={compileError}
        invalidReason={invalidReason}
      />

      {/*
        Clarification UI now lives in the describe tab next to the source
        textarea — that is where the user phrased the rule and where they
        rephrase. Refine flow swallows clarificationRequired as invalid (see
        treatClarificationAsInvalid), so it never reaches the manual builder.
      */}
      {builtRule && !clarificationQuestion && (
        <div className="bg-muted/20 rounded-lg border p-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-end">
            <div className="min-w-0 flex-1 space-y-2">
              <Label htmlFor="manual-rule-refinement">{t('rules.manual.refineLabel')}</Label>
              <Input
                id="manual-rule-refinement"
                value={refinementInstruction}
                maxLength={500}
                placeholder={t('rules.manual.refinePlaceholder')}
                onChange={(event) => setRefinementInstruction(event.currentTarget.value)}
              />
            </div>
            <Button
              type="button"
              variant="secondary"
              disabled={!refinementInstruction.trim() || isCompiling || isSaving}
              onClick={() => {
                const instruction = refinementInstruction.trim();
                if (!instruction) return;
                void onRefineManualRule(builtRule, instruction);
                setRefinementInstruction('');
              }}
            >
              <Wand2 className="size-4" aria-hidden="true" />
              {t('rules.manual.refineCta')}
            </Button>
          </div>
        </div>
      )}

      <div className="bg-background rounded-lg border p-4">
        <Label htmlFor="manual-rule-name">{t('rules.manual.nameLabel')}</Label>
        <Input
          id="manual-rule-name"
          value={draft.displayName}
          maxLength={160}
          className="mt-2"
          placeholder={t('rules.manual.namePlaceholder')}
          onChange={(event) =>
            onDraftChange({
              ...draft,
              displayName: event.currentTarget.value,
            })
          }
        />
      </div>

      <div className="bg-background rounded-lg border p-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="font-semibold">{t('rules.manual.whenTitle')}</h3>
            <p className="text-muted-foreground text-sm">{t('rules.manual.whenBody')}</p>
          </div>
          <Select
            value={draft.matchOperator}
            onValueChange={(value) =>
              onDraftChange({ ...draft, matchOperator: value as ManualMatchOperator })
            }
          >
            <SelectTrigger className="w-full sm:w-32">
              {draft.matchOperator === 'ALL'
                ? t('rules.manual.operator.all')
                : t('rules.manual.operator.any')}
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">{t('rules.manual.operator.all')}</SelectItem>
              <SelectItem value="ANY">{t('rules.manual.operator.any')}</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="mt-4 space-y-2">
          {draft.conditions.map((condition, index) => (
            <ConditionRow
              key={condition.id}
              condition={condition}
              index={index}
              removable={draft.conditions.length > 1}
              onUpdate={onUpdateCondition}
              onRemove={onRemoveCondition}
            />
          ))}
        </div>

        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-3"
          onClick={() =>
            onDraftChange({
              ...draft,
              conditions: [
                ...draft.conditions,
                { ...emptyCondition(draft.conditions.length), id: `condition-${Date.now()}` },
              ],
            })
          }
        >
          <Plus className="size-4" aria-hidden="true" />
          {t('rules.manual.addCondition')}
        </Button>
      </div>

      <div className="bg-background rounded-lg border p-4">
        <div>
          <h3 className="font-semibold">{t('rules.manual.thenTitle')}</h3>
          <p className="text-muted-foreground text-sm">{t('rules.manual.thenBody')}</p>
        </div>

        <div className="mt-4 space-y-2">
          {draft.actions.map((action, index) => (
            <ActionRow
              key={action.id}
              action={action}
              index={index}
              removable={draft.actions.length > 1}
              onUpdate={onUpdateAction}
              onRemove={onRemoveAction}
            />
          ))}
        </div>

        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-3"
          onClick={() =>
            onDraftChange({
              ...draft,
              actions: [
                ...draft.actions,
                { ...emptyAction(draft.actions.length), id: `action-${Date.now()}` },
              ],
            })
          }
        >
          <Plus className="size-4" aria-hidden="true" />
          {t('rules.manual.addAction')}
        </Button>
      </div>

      <div className="bg-muted/20 rounded-lg border border-dashed p-4">
        <h3 className="font-semibold">{t('rules.manual.advancedTitle')}</h3>
        <div className="mt-3 flex flex-wrap gap-2">
          {[
            t('rules.manual.unsafe.autoSend'),
            t('rules.manual.unsafe.forward'),
            t('rules.manual.unsafe.delete'),
            t('rules.manual.unsafe.webhook'),
          ].map((label) => (
            <Badge
              key={label}
              variant="outline"
              className="bg-background text-muted-foreground rounded-sm px-2 py-1"
            >
              {label}
            </Badge>
          ))}
        </div>
      </div>

      {builtRule && (
        <div className="rounded-lg border bg-[#E7F0EF] p-4 text-[#0a3d3a]">
          <div className="flex items-center gap-2 font-semibold">
            <CheckCircle2 className="size-4" aria-hidden="true" />
            {t('rules.manual.structuredPreview')}
          </div>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            <PreviewColumn
              title={t('rules.list.when')}
              items={draft.conditions.map((condition) =>
                describeCondition(condition, structureCopy),
              )}
            />
            <PreviewColumn
              title={t('rules.list.then')}
              items={draft.actions.map((action) => describeAction(action, structureCopy))}
            />
          </div>
        </div>
      )}

      <div className="flex flex-col items-stretch gap-2 border-t pt-4 sm:flex-row sm:items-center">
        <p className="text-muted-foreground text-xs">{t('rules.manual.saveHint')}</p>
        <Button
          type="button"
          className="sm:ml-auto"
          disabled={!builtRule || isSaving}
          onClick={() => builtRule && void onSaveManualRule(builtRule)}
        >
          {isSaving ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Save className="size-4" aria-hidden="true" />
          )}
          {isSaving ? t('rules.composer.saving') : t('rules.composer.saveDisabledCta')}
        </Button>
      </div>
    </div>
  );
}

function ConditionRow({
  condition,
  index,
  removable,
  onUpdate,
  onRemove,
}: {
  condition: ManualCondition;
  index: number;
  removable: boolean;
  onUpdate: (conditionId: string, patch: Partial<ManualCondition>) => void;
  onRemove: (conditionId: string) => void;
}) {
  const t = useTranslations();
  const translate = t as unknown as (key: string) => string;
  const needsValue = conditionRequiresValue(condition.type);

  return (
    <div className="bg-muted/20 grid gap-2 rounded-md border p-2 sm:grid-cols-[28px_190px_1fr_36px] sm:items-center">
      <div className="text-muted-foreground hidden text-center text-xs font-semibold sm:block">
        {index + 1}
      </div>
      <Select
        value={condition.type}
        onValueChange={(value) => {
          const nextType = value as ManualConditionType;
          onUpdate(condition.id, {
            type: nextType,
            value: conditionRequiresValue(nextType) ? condition.value : '',
          });
        }}
      >
        <SelectTrigger className="w-full">
          {conditionTypeLabel(condition.type, translate)}
        </SelectTrigger>
        <SelectContent>
          {MANUAL_CONDITION_TYPES.map((type) => (
            <SelectItem key={type} value={type}>
              {conditionTypeLabel(type, translate)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {needsValue ? (
        <Input
          value={condition.value}
          maxLength={512}
          aria-label={conditionTypeLabel(condition.type, translate)}
          placeholder={conditionPlaceholder(condition.type, translate)}
          onChange={(event) => onUpdate(condition.id, { value: event.currentTarget.value })}
        />
      ) : (
        <div className="text-muted-foreground bg-background rounded-md border px-3 py-2 text-sm">
          {t('rules.manual.noValueNeeded')}
        </div>
      )}
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="size-8 justify-self-end"
        disabled={!removable}
        aria-label={t('rules.manual.removeCondition')}
        onClick={() => onRemove(condition.id)}
      >
        <Trash2 className="size-4" aria-hidden="true" />
      </Button>
    </div>
  );
}

function ActionRow({
  action,
  index,
  removable,
  onUpdate,
  onRemove,
}: {
  action: ManualAction;
  index: number;
  removable: boolean;
  onUpdate: (actionId: string, patch: Partial<ManualAction>) => void;
  onRemove: (actionId: string) => void;
}) {
  const t = useTranslations();
  const translate = t as unknown as (key: string) => string;
  const needsValue = actionRequiresValue(action.type);

  return (
    <div className="bg-muted/20 grid gap-2 rounded-md border p-2 sm:grid-cols-[28px_190px_1fr_36px] sm:items-center">
      <div className="text-muted-foreground hidden text-center text-xs font-semibold sm:block">
        {index + 1}
      </div>
      <Select
        value={action.type}
        onValueChange={(value) => {
          const nextType = value as ManualActionType;
          onUpdate(action.id, {
            type: nextType,
            value: actionRequiresValue(nextType) ? action.value : '',
          });
        }}
      >
        <SelectTrigger className="w-full">
          <span className="inline-flex items-center gap-2">
            {action.type === 'archive' ? (
              <Archive className="size-4" aria-hidden="true" />
            ) : action.type === 'label' ? (
              <Tags className="size-4" aria-hidden="true" />
            ) : (
              <FileText className="size-4" aria-hidden="true" />
            )}
            {actionTypeLabel(action.type, translate)}
          </span>
        </SelectTrigger>
        <SelectContent>
          {MANUAL_ACTION_TYPES.map((type) => (
            <SelectItem key={type} value={type}>
              <span className="inline-flex items-center gap-2">
                {type === 'archive' ? (
                  <Archive className="size-4" aria-hidden="true" />
                ) : type === 'label' ? (
                  <Tags className="size-4" aria-hidden="true" />
                ) : (
                  <FileText className="size-4" aria-hidden="true" />
                )}
                {actionTypeLabel(type, translate)}
              </span>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {needsValue ? (
        <Input
          value={action.value}
          maxLength={500}
          aria-label={actionTypeLabel(action.type, translate)}
          placeholder={actionPlaceholder(action.type, translate)}
          onChange={(event) => onUpdate(action.id, { value: event.currentTarget.value })}
        />
      ) : (
        <div className="text-muted-foreground bg-background rounded-md border px-3 py-2 text-sm">
          {t('rules.manual.noValueNeeded')}
        </div>
      )}
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="size-8 justify-self-end"
        disabled={!removable}
        aria-label={t('rules.manual.removeAction')}
        onClick={() => onRemove(action.id)}
      >
        <Trash2 className="size-4" aria-hidden="true" />
      </Button>
    </div>
  );
}

function CompiledReview({
  matcherReview,
  actionReview,
  sourceLanguage,
}: {
  matcherReview: string[];
  actionReview: string[];
  sourceLanguage: string | undefined;
}) {
  const t = useTranslations();

  return (
    <div className="bg-muted/30 rounded-lg border p-4" aria-live="polite">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <CheckCircle2 className="text-green size-4 shrink-0" aria-hidden="true" />
        <p className="text-sm font-medium">{t('rules.composer.compiledReview')}</p>
        <Badge variant="outline" className="border-green/30 text-green ml-auto text-xs">
          {sourceLanguage ?? 'rules.v1'}
        </Badge>
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        <PreviewColumn title={t('rules.composer.matcherReview')} items={matcherReview} />
        <PreviewColumn title={t('rules.composer.actionReview')} items={actionReview} action />
      </div>
      <div className="mt-3 flex items-start gap-2 rounded-md border border-amber-500/20 bg-amber-500/5 p-2.5 text-xs leading-5 text-amber-800 dark:text-amber-200">
        <AlertCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
        <span>{t('rules.composer.allowedActionsNote')}</span>
      </div>
    </div>
  );
}

function PreviewColumn({
  title,
  items,
  action = false,
}: {
  title: string;
  items: string[];
  action?: boolean;
}) {
  return (
    <div className="bg-background/70 rounded-md border p-3">
      <p className="text-muted-foreground flex items-center gap-1.5 text-xs font-semibold tracking-wide uppercase">
        {action ? (
          <Zap className="size-3" aria-hidden="true" />
        ) : (
          <Tags className="size-3" aria-hidden="true" />
        )}
        {title}
      </p>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {items.map((item) => (
          <Badge
            key={`${title}-${item}`}
            variant={action ? 'secondary' : 'outline'}
            className="max-w-full text-xs whitespace-normal"
          >
            {item}
          </Badge>
        ))}
      </div>
    </div>
  );
}

function ComposerErrors({
  insufficientCreditError,
  compileError,
  invalidReason,
}: {
  insufficientCreditError: string | null;
  compileError: string | null;
  invalidReason: string | undefined;
}) {
  const t = useTranslations();

  return (
    <div aria-live="polite" className="space-y-2">
      {insufficientCreditError && (
        <Alert variant="warning">
          <AlertCircle className="size-4" aria-hidden="true" />
          <AlertTitle>{t('errors.rules.insufficientCredits')}</AlertTitle>
          <AlertDescription>{insufficientCreditError}</AlertDescription>
        </Alert>
      )}

      {compileError && (
        <Alert variant="destructive">
          <AlertCircle className="size-4" aria-hidden="true" />
          <AlertDescription>{compileError}</AlertDescription>
        </Alert>
      )}

      {invalidReason && (
        <Alert variant="warning">
          <AlertDescription>{t('rules.composer.invalid')}</AlertDescription>
        </Alert>
      )}
    </div>
  );
}

function conditionTypeLabel(type: ManualConditionType, t: (key: string) => string) {
  switch (type) {
    case 'SENDER_DOMAIN':
      return t('rules.manual.condition.SENDER_DOMAIN');
    case 'SENDER_EMAIL':
      return t('rules.manual.condition.SENDER_EMAIL');
    case 'RECIPIENT_TO':
      return t('rules.manual.condition.RECIPIENT_TO');
    case 'SUBJECT_CONTAINS':
      return t('rules.manual.condition.SUBJECT_CONTAINS');
    case 'GMAIL_LABEL_PRESENT':
      return t('rules.manual.condition.GMAIL_LABEL_PRESENT');
    case 'HAS_ATTACHMENT':
      return t('rules.manual.condition.HAS_ATTACHMENT');
    case 'NEWSLETTER_INDICATOR':
      return t('rules.manual.condition.NEWSLETTER_INDICATOR');
    case 'SEMANTIC_INTENT':
      return t('rules.manual.condition.SEMANTIC_INTENT');
  }
}

function conditionPlaceholder(type: ManualConditionType, t: (key: string) => string) {
  switch (type) {
    case 'SENDER_DOMAIN':
      return t('rules.manual.conditionPlaceholder.SENDER_DOMAIN');
    case 'SENDER_EMAIL':
      return t('rules.manual.conditionPlaceholder.SENDER_EMAIL');
    case 'RECIPIENT_TO':
      return t('rules.manual.conditionPlaceholder.RECIPIENT_TO');
    case 'SUBJECT_CONTAINS':
      return t('rules.manual.conditionPlaceholder.SUBJECT_CONTAINS');
    case 'GMAIL_LABEL_PRESENT':
      return t('rules.manual.conditionPlaceholder.GMAIL_LABEL_PRESENT');
    case 'HAS_ATTACHMENT':
      return t('rules.manual.conditionPlaceholder.HAS_ATTACHMENT');
    case 'NEWSLETTER_INDICATOR':
      return t('rules.manual.conditionPlaceholder.NEWSLETTER_INDICATOR');
    case 'SEMANTIC_INTENT':
      return t('rules.manual.conditionPlaceholder.SEMANTIC_INTENT');
  }
}

function actionTypeLabel(type: ManualActionType, t: (key: string) => string) {
  switch (type) {
    case 'label':
      return t('rules.manual.action.label');
    case 'archive':
      return t('rules.manual.action.archive');
    case 'save_draft':
      return t('rules.manual.action.save_draft');
  }
}

function actionPlaceholder(type: ManualActionType, t: (key: string) => string) {
  switch (type) {
    case 'label':
      return t('rules.manual.actionPlaceholder.label');
    case 'archive':
      return t('rules.manual.actionPlaceholder.archive');
    case 'save_draft':
      return t('rules.manual.actionPlaceholder.save_draft');
  }
}
