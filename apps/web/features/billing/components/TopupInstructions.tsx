'use client';

import { useEffect, useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { Clock, Landmark } from 'lucide-react';

import { CopyableField } from '@/features/billing/components/CopyableField';
import type { TopupIntentDetails } from '@/features/billing/components/TopupAmountForm';
import { useTopupCreditWatch } from '@/features/billing/hooks/useTopupCreditWatch';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

type TopupInstructionsProps = {
  intent: TopupIntentDetails;
  baselineCredits: number;
  onCredited: (newBalance: number) => void;
  onExpired: () => void;
};

export function TopupInstructions({
  intent,
  baselineCredits,
  onCredited,
  onExpired,
}: TopupInstructionsProps) {
  const t = useTranslations();
  const locale = useLocale();
  const [now, setNow] = useState(() => Date.now());
  const watch = useTopupCreditWatch({
    baselineCredits,
    expiresAt: intent.expiresAt,
  });

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(interval);
  }, []);

  const remainingMs = useMemo(
    () => Math.max(0, Date.parse(intent.expiresAt) - now),
    [intent.expiresAt, now],
  );

  useEffect(() => {
    if (watch.credited && typeof watch.balance?.availableCredits === 'number') {
      onCredited(watch.balance.availableCredits);
    }
  }, [onCredited, watch.balance?.availableCredits, watch.credited]);

  useEffect(() => {
    if (remainingMs <= 0 || watch.expired) {
      onExpired();
    }
  }, [onExpired, remainingMs, watch.expired]);

  return (
    <Card data-testid="topup-instructions-step">
      <CardHeader>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1">
            <CardTitle>{t('billing.topup.waiting.heading')}</CardTitle>
            <CardDescription>{t('billing.topup.waiting.body')}</CardDescription>
          </div>
          <div className="bg-accent-soft text-accent flex size-10 items-center justify-center rounded-lg">
            <Landmark className="size-5" aria-hidden="true" />
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="bg-muted/30 rounded-lg border p-4">
          <h2 className="text-foreground text-base font-semibold">
            {t('billing.topup.qr.heading')}
          </h2>
          <p className="text-muted-foreground mt-1 text-sm leading-6">
            {t('billing.topup.qr.body')}
          </p>
        </div>

        <div className="grid gap-3">
          <CopyableField label={t('billing.topup.reference.label')} value={intent.code} />
          <CopyableField
            label={t('billing.topup.amountVnd.label')}
            value={String(intent.amountVnd)}
            displayValue={formatVnd(intent.amountVnd, locale)}
          />
          <CopyableField label={t('billing.topup.emv.label')} value={intent.qrPayload} multiline />
        </div>

        <div className="text-muted-foreground flex flex-col gap-2 rounded-lg border p-3 text-sm sm:flex-row sm:items-center sm:justify-between">
          <span className="inline-flex items-center gap-2">
            <Clock className="size-4" aria-hidden="true" />
            {t('billing.topup.expiresIn', { time: formatRemaining(remainingMs) })}
          </span>
          <span className="font-mono text-xs">{formatExpiry(intent.expiresAt, locale)}</span>
        </div>
      </CardContent>
    </Card>
  );
}

function formatVnd(value: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
}

function formatExpiry(value: string, locale: string): string {
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return value;
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(timestamp);
}

function formatRemaining(value: number): string {
  const totalSeconds = Math.ceil(value / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}
