'use client';

import { useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import {
  AlertTriangle,
  CheckCircle2,
  Loader2,
  Pause,
  RefreshCcw,
  Sparkles,
  XCircle,
} from 'lucide-react';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/states/EmptyState';
import { Skeleton } from '@/components/ui/skeleton';
import { ErrorCode } from '@/lib/api/error-codes';
import type { RulePreviewResponse, RuleTestMessage } from '@/features/rules/api/rules-api';
import { useTestMessages, useTestSingleMessage } from '@/features/rules/hooks/use-rules';
import { cn } from '@/lib/utils';

type SampleSize = 10 | 20;

const SAMPLE_SIZES = [10, 20] as const;

type RowResult = { matched: boolean; actions: string[]; conflicts: number };

const dateFormatter = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' });

export function GmailRuleTester({ enabledRulesCount }: { enabledRulesCount: number }) {
  const t = useTranslations();
  const [sampleSize, setSampleSize] = useState<SampleSize>(10);
  const messagesQuery = useTestMessages(sampleSize, enabledRulesCount > 0);
  const testSingleMessage = useTestSingleMessage();

  const [resultsByMessageId, setResultsByMessageId] = useState<Record<string, RowResult>>({});
  const [runningById, setRunningById] = useState<Record<string, boolean>>({});
  const [isRunningAll, setIsRunningAll] = useState(false);
  const stopRequestedRef = useRef(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const messages = messagesQuery.data?.messages ?? [];

  async function runOne(message: RuleTestMessage): Promise<'ok' | 'stop'> {
    setRunningById((previous) => ({ ...previous, [message.gmailMessageId]: true }));
    try {
      const response = await testSingleMessage.mutateAsync({
        gmailMessageId: message.gmailMessageId,
        gmailThreadId: message.gmailThreadId,
      });
      setResultsByMessageId((previous) => ({
        ...previous,
        [message.gmailMessageId]: toRowResult(response),
      }));
      setActionError(null);
      return 'ok';
    } catch (error) {
      // Stop the run-all loop on credit/connection failures — retrying every
      // remaining message would just repeat the same error and burn nothing useful.
      if (apiErrorCode(error) === ErrorCode.BillingInsufficient) {
        setActionError(t('errors.rules.insufficientCredits'));
        return 'stop';
      }
      if (apiErrorCode(error) === ErrorCode.RulesGmailUnavailable) {
        setActionError(t('errors.rules.gmail.unavailable'));
        return 'stop';
      }
      setActionError(t('errors.rules.testGmail.generic'));
      return 'stop';
    } finally {
      setRunningById((previous) => ({ ...previous, [message.gmailMessageId]: false }));
    }
  }

  async function handleTestAll() {
    setActionError(null);
    stopRequestedRef.current = false;
    setIsRunningAll(true);
    for (const message of messages) {
      if (stopRequestedRef.current) break;
      if (resultsByMessageId[message.gmailMessageId]) continue;
      const outcome = await runOne(message);
      if (outcome === 'stop') break;
    }
    setIsRunningAll(false);
  }

  function handleStop() {
    stopRequestedRef.current = true;
    setIsRunningAll(false);
  }

  function handleReload() {
    setResultsByMessageId({});
    setActionError(null);
    void messagesQuery.refetch();
  }

  const gmailUnavailable =
    messagesQuery.isError && apiErrorCode(messagesQuery.error) === ErrorCode.RulesGmailUnavailable;

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('rules.preview.title')}</CardTitle>
        <CardDescription>
          {t('rules.preview.testingEnabledCount', { count: enabledRulesCount })}
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-1">
            <p className="text-sm font-medium">{t('rules.preview.sampleSize')}</p>
            <div className="bg-background border-border flex w-fit items-center gap-0.5 rounded-xl border p-1 shadow-sm">
              {SAMPLE_SIZES.map((size) => {
                const isActive = sampleSize === size;
                return (
                  <Button
                    key={size}
                    type="button"
                    variant={isActive ? 'default' : 'ghost'}
                    size="sm"
                    className={cn(
                      'h-8 w-11 rounded-lg border text-sm font-medium transition-all',
                      isActive
                        ? 'border-accent bg-accent text-accent-foreground font-bold shadow-sm'
                        : 'bg-background border-border text-muted-foreground hover:bg-muted hover:text-foreground',
                    )}
                    onClick={() => {
                      setSampleSize(size);
                      setResultsByMessageId({});
                    }}
                    disabled={isRunningAll}
                  >
                    {size}
                  </Button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleReload}
              disabled={messagesQuery.isFetching || isRunningAll}
            >
              {messagesQuery.isFetching ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <RefreshCcw className="size-4" aria-hidden="true" />
              )}
              {t('rules.testGmail.reload')}
            </Button>
            {isRunningAll ? (
              <Button type="button" variant="outline" size="sm" onClick={handleStop}>
                <Pause className="size-4" aria-hidden="true" />
                {t('rules.testGmail.stop')}
              </Button>
            ) : (
              <Button
                type="button"
                size="sm"
                onClick={handleTestAll}
                disabled={messages.length === 0 || messagesQuery.isFetching}
              >
                <Sparkles className="size-4" aria-hidden="true" />
                {t('rules.testGmail.testAll')}
              </Button>
            )}
          </div>
        </div>

        {gmailUnavailable && (
          <Alert variant="warning">
            <AlertTriangle className="size-4" aria-hidden="true" />
            <AlertTitle>{t('errors.rules.gmail.unavailable')}</AlertTitle>
          </Alert>
        )}

        {actionError && (
          <Alert variant="destructive">
            <AlertTriangle className="size-4" aria-hidden="true" />
            <AlertDescription>{actionError}</AlertDescription>
          </Alert>
        )}

        {messagesQuery.isLoading ? (
          <LoadingRows />
        ) : messages.length === 0 && !gmailUnavailable ? (
          <EmptyState
            heading={t('rules.testGmail.empty.heading')}
            body={t('rules.testGmail.empty.body')}
            className="min-h-32 px-4 py-8"
          />
        ) : (
          <div className="divide-y overflow-hidden rounded-lg border">
            {messages.map((message) => (
              <MessageRow
                key={message.gmailMessageId}
                message={message}
                result={resultsByMessageId[message.gmailMessageId]}
                isRunning={Boolean(runningById[message.gmailMessageId])}
                disabled={isRunningAll}
                onTest={() => void runOne(message)}
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function MessageRow({
  message,
  result,
  isRunning,
  disabled,
  onTest,
}: {
  message: RuleTestMessage;
  result: RowResult | undefined;
  isRunning: boolean;
  disabled: boolean;
  onTest: () => void;
}) {
  const t = useTranslations();
  return (
    <article
      className={cn(
        'flex items-start gap-3 px-3 py-2.5 text-sm transition-colors',
        isRunning && 'bg-muted/40 animate-pulse',
      )}
    >
      <div className="w-40 shrink-0">
        <p className="truncate font-medium">
          {senderDisplayName(message.sanitizedSenderEmail || message.sanitizedSenderDomain)}
        </p>
        <p className="text-muted-foreground truncate text-xs">{message.sanitizedSenderDomain}</p>
      </div>
      <div className="min-w-0 flex-1 space-y-1.5">
        <p className="min-w-0 truncate">{message.sanitizedSubjectExcerpt}</p>
        {result && (
          <div className="flex flex-wrap items-center gap-1.5">
            {result.matched ? (
              <span className="bg-primary/15 text-primary inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-semibold">
                <CheckCircle2 className="size-3" aria-hidden="true" />
                {t('rules.testCustom.result.matched')}
              </span>
            ) : (
              <span className="bg-muted text-muted-foreground inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-semibold">
                <XCircle className="size-3" aria-hidden="true" />
                {t('rules.testCustom.result.notMatched')}
              </span>
            )}
            {result.actions.map((action) => (
              <span
                key={action}
                className="bg-primary/10 text-primary rounded-full px-2 py-0.5 text-[10px] font-medium"
              >
                {action}
              </span>
            ))}
            {result.conflicts > 0 && (
              <span className="bg-warning/10 text-warning rounded-sm px-1.5 py-0.5 text-[10px]">
                {t('rules.preview.stat.conflicts')}: {result.conflicts}
              </span>
            )}
          </div>
        )}
      </div>
      <time
        className="text-muted-foreground w-12 shrink-0 pt-0.5 text-right text-xs"
        dateTime={message.internalDate}
      >
        {formatDate(message.internalDate)}
      </time>
      <Button
        type="button"
        variant={result ? 'outline' : 'default'}
        size="sm"
        className="shrink-0"
        disabled={isRunning || disabled}
        onClick={onTest}
      >
        {isRunning ? (
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
        ) : result ? (
          <RefreshCcw className="size-4" aria-hidden="true" />
        ) : (
          <Sparkles className="size-4" aria-hidden="true" />
        )}
        {result ? t('rules.testGmail.retestRow') : t('rules.testGmail.testRow')}
      </Button>
    </article>
  );
}

function LoadingRows() {
  return (
    <div className="divide-y overflow-hidden rounded-lg border">
      {Array.from({ length: 5 }).map((_, index) => (
        <div key={index} className="flex items-center gap-3 p-3">
          <div className="min-w-0 flex-1 space-y-2">
            <Skeleton className="h-3.5 w-40" />
            <Skeleton className="h-3.5 w-full max-w-md" />
          </div>
          <Skeleton className="h-8 w-16 shrink-0" />
        </div>
      ))}
    </div>
  );
}

function toRowResult(response: RulePreviewResponse): RowResult {
  const row = response.rows[0];
  const actions = (row?.proposedActionChips ?? []).flatMap((chip) => {
    const label = chip.safeLabel ?? chip.actionTypeId ?? '';
    const normalized = label.startsWith('label:') ? label.replace('label:', '') : label;
    return normalized ? [normalized] : [];
  });
  return {
    matched: row?.matched ?? false,
    actions,
    conflicts: row?.conflictChips?.length ?? 0,
  };
}

function apiErrorCode(error: unknown): string | undefined {
  if (
    error !== null &&
    typeof error === 'object' &&
    typeof (error as { code?: unknown }).code === 'string'
  ) {
    return (error as { code: string }).code;
  }
  return undefined;
}

function senderDisplayName(emailOrDomain: string): string {
  if (!emailOrDomain) return '';
  const atIndex = emailOrDomain.indexOf('@');
  return atIndex > 0 ? emailOrDomain.slice(0, atIndex) : emailOrDomain;
}

function formatDate(value: string | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return dateFormatter.format(date);
}
