'use client';

import { FormEvent, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { Loader2 } from 'lucide-react';

import { Alert, AlertDescription } from '@/components/ui/alert';
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
import type { TopupIntentResponse } from '@/features/billing/api/billing-api';
import { useCreateTopupIntent } from '@/features/billing/hooks/useCreateTopupIntent';

export type TopupIntentDetails = {
  code: string;
  amountVnd: number;
  expiresAt: string;
  qrPayload: string;
};

type TopupAmountFormProps = {
  baselineCredits: number;
  onIntentCreated: (intent: TopupIntentDetails, baselineCredits: number) => void;
};

export function TopupAmountForm({ baselineCredits, onIntentCreated }: TopupAmountFormProps) {
  const t = useTranslations();
  const locale = useLocale();
  const createIntent = useCreateTopupIntent();
  const [amount, setAmount] = useState('20000');
  const [validationError, setValidationError] = useState<string | null>(null);

  async function createTopupIntentForAmount() {
    const parsedAmount = Number(amount);

    const packageCode = packageCodeForAmount(parsedAmount);
    if (!packageCode) {
      setValidationError(t('billing.topup.amount.error.minimum'));
      return;
    }

    setValidationError(null);

    try {
      const response = await createIntent.mutateAsync(packageCode);
      const intent = normalizeIntent(response);
      if (!intent) {
        setValidationError(t('billing.topup.amount.error.invalidResponse'));
        return;
      }
      onIntentCreated(intent, baselineCredits);
    } catch {
      setValidationError(t('billing.topup.amount.error.generic'));
    }
  }

  async function submitTopupIntent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await createTopupIntentForAmount();
  }

  return (
    <Card data-testid="topup-amount-step">
      <CardHeader>
        <CardTitle>{t('billing.topup.amount.title')}</CardTitle>
        <CardDescription>{t('billing.topup.amount.body')}</CardDescription>
      </CardHeader>
      <form onSubmit={submitTopupIntent}>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="topup-amount">{t('billing.topup.amount.label')}</Label>
            <Input
              id="topup-amount"
              type="number"
              inputMode="numeric"
              min={1}
              step={1000}
              value={amount}
              aria-invalid={Boolean(validationError)}
              aria-describedby={validationError ? 'topup-amount-error' : undefined}
              onChange={(event) => setAmount(event.currentTarget.value)}
            />
            <p className="text-muted-foreground text-xs">
              {t('billing.topup.amount.preview', {
                amount: formatVnd(Number(amount), locale),
              })}
            </p>
          </div>

          {validationError ? (
            <Alert variant="warning" id="topup-amount-error">
              <AlertDescription>{validationError}</AlertDescription>
            </Alert>
          ) : null}
        </CardContent>
        <CardFooter>
          <Button
            type="button"
            variant="accent"
            disabled={createIntent.isPending}
            onClick={() => void createTopupIntentForAmount()}
          >
            {createIntent.isPending ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : null}
            {createIntent.isPending
              ? t('billing.topup.amount.submitting')
              : t('billing.topup.amount.cta')}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}

function normalizeIntent(response: TopupIntentResponse): TopupIntentDetails | null {
  if (!response.orderCode || !response.amountVnd || !response.expiresAt || !response.qrPayload) {
    return null;
  }

  return {
    code: response.orderCode,
    amountVnd: response.amountVnd,
    expiresAt: response.expiresAt,
    qrPayload: response.qrPayload,
  };
}

function packageCodeForAmount(amountVnd: number): string | null {
  if (amountVnd === 10000) return 'PKG_10K';
  if (amountVnd === 20000) return 'PKG_20K';
  if (amountVnd === 50000) return 'PKG_50K';
  return null;
}

function formatVnd(value: number, locale: string): string {
  const amount = Number.isFinite(value) ? value : 0;
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(amount);
}
