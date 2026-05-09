'use client';

import { useTranslations } from 'next-intl';
import { AlertCircle, HelpCircle, Loader2, Save, Wand2 } from 'lucide-react';

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

type Props = {
  sourceText: string;
  clarificationAnswer: string;
  compileResult: RuleCompileResult | null;
  compileError: string | null;
  insufficientCreditError: string | null;
  isCompiling: boolean;
  isSaving: boolean;
  onSourceTextChange: (sourceText: string) => void;
  onClarificationAnswerChange: (answer: string) => void;
  onCompile: () => void;
  onAnswerClarification: () => void;
  onSaveDisabledRule: () => void;
};

export function RuleComposer({
  sourceText,
  clarificationAnswer,
  compileResult,
  compileError,
  insufficientCreditError,
  isCompiling,
  isSaving,
  onSourceTextChange,
  onClarificationAnswerChange,
  onCompile,
  onAnswerClarification,
  onSaveDisabledRule,
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

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('rules.composer.title')}</CardTitle>
        <CardDescription>{t('rules.page.safetyNote')}</CardDescription>
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
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline" className="border-green/30 text-green">
                {compiled.sourceLanguage ?? 'rules.v1'}
              </Badge>
              <p className="text-sm font-medium">{t('rules.composer.compiledReview')}</p>
            </div>
            <div className="mt-3 grid gap-3 md:grid-cols-2">
              <ReviewBlock title={t('rules.composer.matcherReview')} items={matcherReview} />
              <ReviewBlock title={t('rules.composer.actionReview')} items={actionReview} />
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
      </CardFooter>
    </Card>
  );
}

function ReviewBlock({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="min-w-0 space-y-2">
      <p className="text-muted-foreground text-xs font-medium">{title}</p>
      <div className="flex flex-wrap gap-1.5">
        {items.map((item) => (
          <Badge
            key={`${title}-${item}`}
            variant="outline"
            className="max-w-full whitespace-normal"
          >
            {item}
          </Badge>
        ))}
      </div>
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
    return [jsonText.slice(0, 80)];
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
