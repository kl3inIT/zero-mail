'use client';

import { Check, Loader2, Sparkles } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';

import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type {
  BillingPackageResponse,
  TopupIntentResponse,
} from '@/features/billing/api/billing-api';
import { useBillingPackages } from '@/features/billing/hooks/useBillingPackages';
import { useCreateTopupIntent } from '@/features/billing/hooks/useCreateTopupIntent';
import { cn } from '@/lib/utils';

export type TopupIntentDetails = {
  code: string;
  packageCode?: string;
  packageName?: string;
  amountVnd: number;
  creditAmount?: number;
  expiresAt: string;
  bankCode?: string;
  bankName?: string;
  accountNumber?: string;
  accountName?: string;
  transferContent?: string;
  qrPayload: string;
};

type TopupPackageSelectorProps = {
  baselineCredits: number;
  onIntentCreated: (intent: TopupIntentDetails, baselineCredits: number) => void;
};

const FEATURE_KEYS = [
  'billing.topup.packages.feature.instant',
  'billing.topup.packages.feature.webhook',
  'billing.topup.packages.feature.expiry',
] as const;
const EMPTY_PACKAGES: BillingPackageResponse[] = [];

export function TopupPackageSelector({
  baselineCredits,
  onIntentCreated,
}: TopupPackageSelectorProps) {
  const t = useTranslations();
  const locale = useLocale();
  const packagesQuery = useBillingPackages();
  const createIntent = useCreateTopupIntent();
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const packages = packagesQuery.data ?? EMPTY_PACKAGES;
  const selectedPackage =
    packages.find((billingPackage) => billingPackage.code === selectedCode) ??
    packages[1] ??
    packages[0];
  const highlightedCode = useMemo(() => recommendedCode(packages), [packages]);

  async function submitSelectedPackage(packageCode: string) {
    setSelectedCode(packageCode);
    setValidationError(null);

    try {
      const response = await createIntent.mutateAsync(packageCode);
      const intent = normalizeIntent(response);
      if (!intent) {
        setValidationError(t('billing.topup.packages.error.invalidResponse'));
        return;
      }
      onIntentCreated(intent, baselineCredits);
    } catch {
      setValidationError(t('billing.topup.packages.error.generic'));
    }
  }

  if (packagesQuery.isPending) {
    return (
      <div className="grid gap-4 md:grid-cols-3" data-testid="topup-package-loading">
        {[0, 1, 2].map((item) => (
          <div key={item} className="bg-muted/30 h-[420px] animate-pulse rounded-xl border" />
        ))}
      </div>
    );
  }

  if (packagesQuery.isError) {
    return (
      <Alert variant="warning">
        <AlertDescription>{t('billing.topup.packages.error.load')}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-6" data-testid="topup-package-step">
      <div className="grid gap-4 md:grid-cols-3">
        {packages.map((billingPackage, index) => {
          const isHighlighted = billingPackage.code === highlightedCode;
          const isSelected = billingPackage.code === selectedPackage?.code;
          const isSubmitting = createIntent.isPending && selectedCode === billingPackage.code;

          return (
            <article
              key={billingPackage.code}
              className={cn(
                'bg-card relative flex min-h-[430px] flex-col overflow-hidden rounded-xl border p-6 shadow-sm transition',
                packageTone(index),
                isHighlighted && 'border-primary/40 shadow-primary/10 shadow-md',
                isSelected && 'ring-primary/50 ring-2',
              )}
            >
              {isHighlighted ? (
                <Badge className="bg-foreground text-background absolute top-4 right-4">
                  <Sparkles className="size-3" aria-hidden="true" />
                  {t('billing.topup.packages.recommended')}
                </Badge>
              ) : null}

              <div className="space-y-5">
                <div className="space-y-2 pr-24">
                  <h2 className="text-foreground text-lg font-semibold">{billingPackage.name}</h2>
                  <p className="text-muted-foreground min-h-10 text-sm leading-5">
                    {billingPackage.description ?? t('billing.topup.packages.defaultDescription')}
                  </p>
                </div>

                <div>
                  <p className="text-muted-foreground text-sm">
                    {t('billing.topup.packages.startsAt')}
                  </p>
                  <div className="mt-1 flex items-end gap-2">
                    <span className="text-foreground text-4xl font-semibold tracking-normal">
                      {formatVnd(billingPackage.priceVnd ?? 0, locale)}
                    </span>
                  </div>
                  <p className="text-muted-foreground mt-2 text-sm">
                    {t('billing.topup.packages.credits', {
                      credits: billingPackage.creditAmount ?? 0,
                    })}
                  </p>
                </div>
              </div>

              <Button
                type="button"
                variant={isHighlighted ? 'accent' : 'outline'}
                className="mt-6 w-full"
                disabled={createIntent.isPending}
                onClick={() => void submitSelectedPackage(billingPackage.code ?? '')}
              >
                {isSubmitting ? (
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                ) : null}
                {t('billing.topup.packages.cta')}
              </Button>

              <div className="mt-6 border-t pt-5">
                <p className="text-foreground text-sm font-medium">
                  {t('billing.topup.packages.includes')}
                </p>
                <ul className="mt-4 space-y-3">
                  {FEATURE_KEYS.map((featureKey) => (
                    <li key={featureKey} className="flex gap-3 text-sm leading-5">
                      <Check className="text-primary mt-0.5 size-4 shrink-0" aria-hidden="true" />
                      <span className="text-muted-foreground">{t(featureKey)}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </article>
          );
        })}
      </div>

      {validationError ? (
        <Alert variant="warning">
          <AlertDescription>{validationError}</AlertDescription>
        </Alert>
      ) : null}
    </div>
  );
}

function normalizeIntent(response: TopupIntentResponse): TopupIntentDetails | null {
  if (!response.orderCode || !response.amountVnd || !response.expiresAt) {
    return null;
  }

  return {
    code: response.orderCode,
    packageCode: response.packageCode,
    packageName: response.packageName,
    amountVnd: response.amountVnd,
    creditAmount: response.creditAmount,
    expiresAt: response.expiresAt,
    bankCode: response.bankCode,
    bankName: response.bankName,
    accountNumber: response.accountNumber,
    accountName: response.accountName,
    transferContent: response.transferContent,
    qrPayload: response.qrPayload ?? '',
  };
}

function recommendedCode(packages: BillingPackageResponse[]): string | undefined {
  return packages[Math.min(1, packages.length - 1)]?.code;
}

function packageTone(index: number): string {
  if (index === 0)
    return 'bg-[linear-gradient(180deg,rgba(255,237,226,0.72),rgba(255,255,255,0.95))]';
  if (index === 1)
    return 'bg-[linear-gradient(180deg,rgba(229,236,255,0.78),rgba(255,255,255,0.96))]';
  return 'bg-[linear-gradient(180deg,rgba(226,250,247,0.72),rgba(255,255,255,0.95))]';
}

function formatVnd(value: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
}
