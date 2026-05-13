'use client';

import { useEffect, useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { Clock, Landmark } from 'lucide-react';
import Image from 'next/image';

import { CopyableField } from '@/features/billing/components/CopyableField';
import type { TopupIntentDetails } from '@/features/billing/components/TopupPackageSelector';
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
  const qrImageUrl = buildSepayQrUrl(intent);

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
        <div className="grid gap-4 lg:grid-cols-[360px_minmax(0,1fr)]">
          <div className="bg-background rounded-xl border p-4">
            <div className="border-primary/40 flex h-full min-h-[360px] flex-col items-center justify-center gap-4 rounded-lg border bg-white p-5">
              <div className="text-primary text-xl font-semibold">SePay</div>
              {qrImageUrl ? (
                <Image
                  src={qrImageUrl}
                  width={256}
                  height={256}
                  alt={t('billing.topup.qr.alt')}
                  className="h-64 w-64 object-contain"
                  unoptimized
                />
              ) : (
                <div className="text-muted-foreground bg-muted/30 flex h-64 w-64 items-center justify-center rounded-lg border text-center text-sm">
                  {t('billing.topup.qr.missing')}
                </div>
              )}
              <p className="text-muted-foreground text-center text-xs leading-5">
                {t('billing.topup.qr.body')}
              </p>
            </div>
          </div>

          <div className="bg-background overflow-hidden rounded-xl border">
            <PaymentInfoRow label={t('billing.topup.bank.label')} value={formatBank(intent)} />
            <PaymentInfoRow
              label={t('billing.topup.accountName.label')}
              value={intent.accountName}
            />
            <PaymentInfoRow
              label={t('billing.topup.accountNumber.label')}
              value={intent.accountNumber}
              copyValue={intent.accountNumber}
            />
            <PaymentInfoRow
              label={t('billing.topup.amountVnd.label')}
              value={formatVnd(intent.amountVnd, locale)}
              copyValue={String(intent.amountVnd)}
            />
            <PaymentInfoRow
              label={t('billing.topup.transferContent.label')}
              value={intent.transferContent}
              copyValue={intent.transferContent}
            />
            <PaymentInfoRow
              label={t('billing.topup.reference.label')}
              value={intent.code}
              copyValue={intent.code}
            />
            {intent.packageName ? (
              <PaymentInfoRow
                label={t('billing.topup.package.label')}
                value={
                  intent.creditAmount
                    ? t('billing.topup.package.display', {
                        name: intent.packageName,
                        credits: intent.creditAmount,
                      })
                    : intent.packageName
                }
              />
            ) : null}
          </div>
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

function PaymentInfoRow({
  label,
  value,
  copyValue,
}: {
  label: string;
  value?: string;
  copyValue?: string;
}) {
  if (!value) return null;
  return (
    <div className="grid gap-2 border-b p-4 last:border-b-0 sm:grid-cols-[180px_minmax(0,1fr)] sm:items-center">
      <div className="text-muted-foreground text-sm font-medium">{label}</div>
      <div className="min-w-0">
        {copyValue ? (
          <CopyableField
            label={label}
            value={copyValue}
            displayValue={value}
            className="bg-muted/30 border-0 p-2"
          />
        ) : (
          <p className="text-foreground text-sm font-medium break-words">{value}</p>
        )}
      </div>
    </div>
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

function formatBank(intent: TopupIntentDetails): string | undefined {
  if (intent.bankName && intent.bankCode) return `${intent.bankName} (${intent.bankCode})`;
  return intent.bankName ?? intent.bankCode;
}

function buildSepayQrUrl(intent: TopupIntentDetails): string | null {
  if (!intent.accountNumber || !intent.bankCode || !intent.amountVnd || !intent.transferContent) {
    return null;
  }
  const params = new URLSearchParams({
    acc: intent.accountNumber,
    bank: intent.bankCode,
    amount: String(intent.amountVnd),
    des: intent.transferContent,
  });
  return `https://qr.sepay.vn/img?${params.toString()}`;
}

function formatRemaining(value: number): string {
  const totalSeconds = Math.ceil(value / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}
