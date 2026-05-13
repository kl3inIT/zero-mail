'use client';

import { useTranslations } from 'next-intl';
import {
  AlertCircle,
  CheckCircle2,
  Filter,
  HelpCircle,
  Loader2,
  Play,
  Save,
  Wand2,
  Zap,
} from 'lucide-react';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { RuleCompileResult } from '@/features/rules/api/rules-api';
import { cn } from '@/lib/utils';

type Props = {
  sourceText: string;
  clarificationAnswer: string;
  compileResult: RuleCompileResult | null;
  compileError: string | null;
  insufficientCreditError: string | null;
  isCompiling: boolean;
  isSaving: boolean;
  canPreview: boolean;
  onSourceTextChange: (sourceText: string) => void;
  onClarificationAnswerChange: (answer: string) => void;
  onCompile: () => void;
  onAnswerClarification: () => void;
  onSaveDisabledRule: () => void;
  onOpenPreview: () => void;
};

export function RuleComposer({
  sourceText,
  clarificationAnswer,
  compileResult,
  compileError,
  insufficientCreditError,
  isCompiling,
  isSaving,
  canPreview,
  onSourceTextChange,
  onClarificationAnswerChange,
  onCompile,
  onAnswerClarification,
  onSaveDisabledRule,
  onOpenPreview,
}: Props) {
  const t = useTranslations();
  const hasSourceText = sourceText.trim().length > 0;
  const clarification =
    compileResult?.status === 'clarificationRequired' ? compileResult.clarification : null;
  const compiled = compileResult?.status === 'compiled' ? compileResult.compiled : null;
  const invalid = compileResult?.status === 'invalid' ? compileResult.invalid : null;

  const matcherReview = summarizeCompiledJson(
    compiled?.matcherAst,
    t('rules.composer.matcherReview'),
  );
  const actionReview = summarizeCompiledJson(
    compiled?.actionIntents,
    t('rules.composer.actionReview'),
  );

  const workflowStep: 1 | 2 | 3 =
    compileResult?.status === 'compiled'
      ? 3
      : compileResult?.status === 'clarificationRequired'
        ? 2
        : 1;

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('rules.composer.title')}</CardTitle>
        <CardDescription>{t('rules.page.safetyNote')}</CardDescription>
        <WorkflowStepBar
          step={workflowStep}
          writeLabel={t('rules.composer.step.write')}
          compileLabel={t('rules.composer.step.compile')}
          saveLabel={t('rules.composer.step.save')}
        />
      </CardHeader>

      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="rules-source-text">{t('rules.composer.sourceLabel')}</Label>
          <Textarea
            id="rules-source-text"
            aria-label={t('rules.composer.sourceLabel')}
            value={sourceText}
            placeholder={t('rules.composer.sourcePlaceholder')}
            disabled={isCompiling}
            className="min-h-28 resize-y"
            onChange={(event) => onSourceTextChange(event.currentTarget.value)}
          />
        </div>

        {clarification?.question && (
          <Alert className="border-warning/40 bg-warning-soft/50 text-warning">
            <HelpCircle className="size-4" aria-hidden="true" />
            <AlertTitle>{clarification.question}</AlertTitle>
            <AlertDescription className="space-y-3 pt-2">
              <Label htmlFor="rules-clarification-answer">{t('rules.composer.answerLabel')}</Label>
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
          <div className="bg-muted/30 rounded-lg border p-3" aria-live="polite">
            <div className="mb-3 flex flex-wrap items-center gap-2">
              <CheckCircle2 className="text-green size-4 shrink-0" aria-hidden="true" />
              <p className="text-sm font-medium">{t('rules.composer.compiledReview')}</p>
              <Badge variant="outline" className="border-green/30 text-green ml-auto text-xs">
                {compiled.sourceLanguage ?? 'rules.v1'}
              </Badge>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <div className="space-y-2">
                <p className="text-muted-foreground flex items-center gap-1.5 text-xs font-medium">
                  <Filter className="size-3" aria-hidden="true" />
                  {t('rules.composer.matcherReview')}
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {matcherReview.map((item) => (
                    <Badge
                      key={`m-${item}`}
                      variant="outline"
                      className="max-w-full text-xs whitespace-normal"
                    >
                      {item}
                    </Badge>
                  ))}
                </div>
              </div>
              <div className="space-y-2">
                <p className="text-muted-foreground flex items-center gap-1.5 text-xs font-medium">
                  <Zap className="size-3" aria-hidden="true" />
                  {t('rules.composer.actionReview')}
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {actionReview.map((item) => (
                    <Badge
                      key={`a-${item}`}
                      variant="secondary"
                      className="max-w-full text-xs whitespace-normal"
                    >
                      {item}
                    </Badge>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

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

          {invalid?.reason && (
            <Alert variant="warning">
              <AlertDescription>{t('rules.composer.invalid')}</AlertDescription>
            </Alert>
          )}
        </div>
      </CardContent>

      <CardFooter className="flex flex-col items-stretch gap-2 sm:flex-row">
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
          onClick={onSaveDisabledRule}
        >
          {isSaving ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <Save className="size-4" aria-hidden="true" />
          )}
          {isSaving ? t('rules.composer.saving') : t('rules.composer.saveDisabledCta')}
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={!canPreview}
          className="sm:ml-auto"
          onClick={onOpenPreview}
        >
          <Play className="size-4" aria-hidden="true" />
          {t('rules.preview.previewCta')}
        </Button>
      </CardFooter>
    </Card>
  );
}

function WorkflowStepBar({
  step,
  writeLabel,
  compileLabel,
  saveLabel,
}: {
  step: 1 | 2 | 3;
  writeLabel: string;
  compileLabel: string;
  saveLabel: string;
}) {
  const steps = [writeLabel, compileLabel, saveLabel] as const;
  return (
    <div className="mt-3 flex items-center gap-1" aria-label="Rule creation workflow">
      {steps.map((label, index) => {
        const n = (index + 1) as 1 | 2 | 3;
        const isDone = n < step;
        const isCurrent = n === step;
        return (
          <div key={label} className="flex min-w-0 flex-1 items-center gap-1">
            {index > 0 && <div className="bg-border h-px flex-1" aria-hidden="true" />}
            <StepChip label={label} n={n} isDone={isDone} isCurrent={isCurrent} />
          </div>
        );
      })}
    </div>
  );
}

function StepChip({
  label,
  n,
  isDone,
  isCurrent,
}: {
  label: string;
  n: number;
  isDone: boolean;
  isCurrent: boolean;
}) {
  return (
    <div
      className={cn(
        'flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium transition-colors',
        isDone
          ? 'bg-green/10 text-green'
          : isCurrent
            ? 'bg-primary/10 text-primary ring-primary/20 ring-1 ring-inset'
            : 'text-muted-foreground',
      )}
    >
      {isDone ? (
        <CheckCircle2 className="size-3 shrink-0" aria-hidden="true" />
      ) : (
        <span
          className={cn(
            'flex size-3.5 shrink-0 items-center justify-center rounded-full text-[9px] font-bold',
            isCurrent ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground',
          )}
          aria-hidden="true"
        >
          {n}
        </span>
      )}
      {label}
    </div>
  );
}

function summarizeCompiledJson(jsonText: string | undefined, fallback: string): string[] {
  if (!jsonText) return [fallback];

  try {
    const parsed = JSON.parse(jsonText) as unknown;
    const values = collectReviewStrings(parsed)
      .filter((value) => value.length > 0)
      .slice(0, 6);
    return values.length > 0 ? values : [fallback];
  } catch {
    // If the server ever returns a string that is not valid JSON, render
    // the generic fallback rather than a raw 80-char slice of the
    // payload (which would surface fragments like `{"schemaVersion":...`
    // as a chip in the "What Zero Mail understood" review card).
    return [fallback];
  }
}

function collectReviewStrings(value: unknown): string[] {
  if (typeof value === 'string') return [humanizeReviewToken(value)];
  if (typeof value === 'number' || typeof value === 'boolean') return [String(value)];
  if (Array.isArray(value)) return value.flatMap(collectReviewStrings);
  if (value === null || typeof value !== 'object') return [];

  return Object.entries(value as Record<string, unknown>).flatMap(([key, entryValue]) => {
    if (key === 'id' || key.toLowerCase().endsWith('id')) return [];
    return collectReviewStrings(entryValue);
  });
}

function humanizeReviewToken(value: string): string {
  return value.replaceAll('_', ' ').replaceAll('-', ' ');
}
