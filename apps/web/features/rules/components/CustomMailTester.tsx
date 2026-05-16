'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { AlertTriangle, CheckCircle2, Loader2, Play, XCircle } from 'lucide-react';

import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/states/EmptyState';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { RuleCustomPreviewResponse } from '@/features/rules/api/rules-api';
import { cn } from '@/lib/utils';

type Props = {
  selectedCount: number;
  isRunning: boolean;
  result: RuleCustomPreviewResponse | null;
  resultError: string | null;
  onClearSelection: () => void;
  onRunTest: (input: { subject: string; body: string }) => void;
};

export function CustomMailTester({
  selectedCount,
  isRunning,
  result,
  resultError,
  onClearSelection,
  onRunTest,
}: Props) {
  const t = useTranslations();
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  function handleRunTest() {
    const trimmedSubject = subject.trim();
    const trimmedBody = body.trim();
    if (!trimmedSubject && !trimmedBody) {
      setLocalError(t('errors.rules.testCustom.subjectOrBodyRequired'));
      return;
    }
    setLocalError(null);
    onRunTest({ subject: trimmedSubject, body: trimmedBody });
  }

  const entries = result?.entries ?? [];

  return (
    <Card data-testid="rules-custom-mail-tester">
      <CardHeader>
        <CardTitle>{t('rules.testCustom.title')}</CardTitle>
        <CardDescription>{t('rules.testCustom.intro')}</CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="custom-mail-subject" className="text-sm font-medium">
            {t('rules.testCustom.subjectLabel')}
          </Label>
          <Input
            id="custom-mail-subject"
            value={subject}
            onChange={(event) => setSubject(event.target.value)}
            placeholder={t('rules.testCustom.subjectPlaceholder')}
            maxLength={500}
            data-testid="custom-mail-subject"
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="custom-mail-body" className="text-sm font-medium">
            {t('rules.testCustom.bodyLabel')}
          </Label>
          <Textarea
            id="custom-mail-body"
            value={body}
            onChange={(event) => setBody(event.target.value)}
            placeholder={t('rules.testCustom.bodyPlaceholder')}
            rows={6}
            maxLength={50_000}
            data-testid="custom-mail-body"
          />
        </div>

        {(localError || resultError) && (
          <Alert variant="destructive">
            <AlertTriangle className="size-4" aria-hidden="true" />
            <AlertDescription>{localError ?? resultError}</AlertDescription>
          </Alert>
        )}

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-muted-foreground flex flex-wrap items-center gap-2 text-xs">
            {selectedCount > 0 ? (
              <>
                <span>{t('rules.testCustom.selectedHint', { count: selectedCount })}</span>
                <button
                  type="button"
                  className="text-primary underline-offset-2 hover:underline"
                  onClick={onClearSelection}
                >
                  {t('rules.testCustom.clearSelection')}
                </button>
              </>
            ) : (
              <span>{t('rules.testCustom.noSelectedHint')}</span>
            )}
          </p>
          <Button
            type="button"
            disabled={isRunning}
            onClick={handleRunTest}
            data-testid="custom-mail-run-test"
          >
            {isRunning ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <Play className="size-4" aria-hidden="true" />
            )}
            {isRunning ? t('rules.testCustom.running') : t('rules.testCustom.runCta')}
          </Button>
        </div>

        {!result ? (
          <EmptyState
            heading={t('rules.testCustom.empty.heading')}
            body={t('rules.testCustom.empty.body')}
            className="min-h-28 px-4 py-6"
          />
        ) : (
          <ResultList entries={entries} />
        )}
      </CardContent>
    </Card>
  );
}

function ResultList({ entries }: { entries: RuleCustomPreviewResponse['entries'] }) {
  const t = useTranslations();
  if (entries.length === 0) {
    return (
      <EmptyState
        heading={t('rules.testCustom.empty.heading')}
        body={t('rules.testCustom.empty.body')}
        className="min-h-28 px-4 py-6"
      />
    );
  }
  const matchedCount = entries.filter((entry) => entry.matched).length;
  return (
    <div className="space-y-3" data-testid="custom-mail-results">
      <p className="text-muted-foreground text-xs font-medium">
        {t('rules.testCustom.result.title', { count: entries.length })}
        <span className="text-foreground/70 ml-2">· {matchedCount} ✓</span>
      </p>
      <div className="divide-y rounded-lg border">
        {entries.map((entry) => (
          <ResultRow key={entry.ruleId} entry={entry} />
        ))}
      </div>
    </div>
  );
}

function ResultRow({ entry }: { entry: RuleCustomPreviewResponse['entries'][number] }) {
  const t = useTranslations();
  const status = entry.matched ? 'matched' : entry.deferred ? 'deferred' : 'noMatch';
  const proposedActions = entry.proposedActionChips
    .map((chip) => chip.safeLabel ?? chip.actionTypeId)
    .filter((label): label is string => Boolean(label));
  const matchedEvidence = entry.matchedEvidenceChips
    .map((chip) => chip.reasonKey ?? chip.matcherNodeId)
    .filter((label): label is string => Boolean(label));
  const deferredEvidence = entry.deferredEvidenceChips
    .map((chip) => chip.reasonKey ?? chip.matcherNodeId)
    .filter((label): label is string => Boolean(label));

  return (
    <article
      className={cn(
        'flex items-start gap-3 px-4 py-3 text-sm',
        status === 'matched' && 'bg-primary/5',
        status === 'noMatch' && 'opacity-70',
      )}
      data-testid={`custom-mail-result-${entry.ruleId}`}
      data-result-status={status}
    >
      <StatusBadge status={status} />
      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate font-semibold">{entry.displayName}</p>
          {!entry.enabled && (
            <Badge variant="outline" className="h-5 rounded-sm px-1.5 text-[10px]">
              {t('rules.testCustom.result.disabled')}
            </Badge>
          )}
        </div>
        {status === 'matched' && proposedActions.length > 0 && (
          <ChipRow
            label={t('rules.testCustom.result.actions')}
            items={proposedActions}
            tone="action"
          />
        )}
        {status === 'matched' && matchedEvidence.length > 0 && (
          <ChipRow
            label={t('rules.testCustom.result.evidence')}
            items={matchedEvidence}
            tone="evidence"
          />
        )}
        {status === 'deferred' && deferredEvidence.length > 0 && (
          <ChipRow
            label={t('rules.testCustom.result.deferredEvidence')}
            items={deferredEvidence}
            tone="deferred"
          />
        )}
        {status === 'noMatch' && (
          <p className="text-muted-foreground text-xs">
            {t('rules.testCustom.result.noMatchHint')}
          </p>
        )}
      </div>
    </article>
  );
}

function StatusBadge({ status }: { status: 'matched' | 'deferred' | 'noMatch' }) {
  const t = useTranslations();
  if (status === 'matched') {
    return (
      <span className="bg-primary/15 text-primary inline-flex shrink-0 items-center gap-1 rounded-md px-2 py-1 text-xs font-semibold">
        <CheckCircle2 className="size-3.5" aria-hidden="true" />
        {t('rules.testCustom.result.matched')}
      </span>
    );
  }
  if (status === 'deferred') {
    return (
      <span className="inline-flex shrink-0 items-center gap-1 rounded-md bg-violet-100 px-2 py-1 text-xs font-semibold text-violet-700 dark:bg-violet-900/30 dark:text-violet-300">
        <AlertTriangle className="size-3.5" aria-hidden="true" />
        {t('rules.testCustom.result.deferred')}
      </span>
    );
  }
  return (
    <span className="bg-muted text-muted-foreground inline-flex shrink-0 items-center gap-1 rounded-md px-2 py-1 text-xs font-semibold">
      <XCircle className="size-3.5" aria-hidden="true" />
      {t('rules.testCustom.result.notMatched')}
    </span>
  );
}

function ChipRow({
  label,
  items,
  tone,
}: {
  label: string;
  items: string[];
  tone: 'action' | 'evidence' | 'deferred';
}) {
  return (
    <div className="flex flex-wrap items-center gap-1.5 text-xs">
      <span className="text-muted-foreground font-medium">{label}:</span>
      {items.map((item) => (
        <span
          key={`${tone}-${item}`}
          className={cn(
            'rounded-sm px-1.5 py-0.5 text-[11px] font-medium',
            tone === 'action' && 'bg-primary/10 text-primary',
            tone === 'evidence' && 'bg-muted text-muted-foreground',
            tone === 'deferred' &&
              'bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-300',
          )}
        >
          {item}
        </span>
      ))}
    </div>
  );
}
