'use client';

import { Check, Loader2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';

import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import type {
  BillingPackageResponse,
  TopupIntentResponse,
} from '@/features/billing/api/billing-api';
import { useBillingPackages } from '@/features/billing/hooks/useBillingPackages';
import { useCreateTopupIntent } from '@/features/billing/hooks/useCreateTopupIntent';
import { formatVnd } from '@/features/billing/util/format-vnd';
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
  const defaultPackage =
    packages.find((billingPackage) => billingPackage.featured) ??
    (packages.length > 0 ? packages[Math.floor(packages.length / 2)] : undefined);
  const selectedPackage =
    packages.find((billingPackage) => billingPackage.code === selectedCode) ?? defaultPackage;

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
      <div className="grid gap-8 md:grid-cols-3" data-testid="topup-package-loading">
        {[0, 1, 2].map((item) => (
          <div key={item} className="bg-muted/20 h-[500px] animate-pulse rounded-[2rem] border" />
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
    <div className="space-y-12" data-testid="topup-package-step">
      <div className="grid gap-8 md:grid-cols-3">
        {packages.map((billingPackage) => {
          const isSelected = billingPackage.code === selectedPackage?.code;
          const isHighlighted = billingPackage.featured || isSelected;
          const isSubmitting = createIntent.isPending && selectedCode === billingPackage.code;

          return (
            <article
              key={billingPackage.code}
              className={cn(
                'group bg-card/40 relative flex min-h-[500px] flex-col overflow-hidden rounded-[2rem] border p-8 shadow-sm backdrop-blur-sm transition-all duration-500 hover:-translate-y-2 hover:shadow-2xl',
                isHighlighted
                  ? 'border-primary/60 ring-primary/20 ring-1'
                  : 'border-border/50 hover:border-primary/30',
              )}
            >
              {/* Background Glow Effect */}
              <div className="bg-primary/5 group-hover:bg-primary/15 absolute -top-24 -right-24 h-48 w-48 rounded-full blur-[80px] transition-all duration-500" />

              <div className="relative flex flex-1 flex-col">
                {billingPackage.featured ? (
                  <div className="bg-primary text-primary-foreground absolute top-0 right-0 rounded-full px-3 py-1 text-[11px] font-bold tracking-wider uppercase">
                    {t('billing.topup.packages.recommended')}
                  </div>
                ) : null}
                <div className="mb-8 space-y-3">
                  <h2 className="text-foreground text-2xl font-bold tracking-tight">
                    {billingPackage.name}
                  </h2>
                  <p className="text-muted-foreground text-sm leading-relaxed">
                    {billingPackage.description ?? t('billing.topup.packages.defaultDescription')}
                  </p>
                </div>

                <div className="mb-8">
                  <div className="flex items-baseline gap-1">
                    <span className="text-foreground text-4xl font-extrabold tracking-tight">
                      {formatVnd(billingPackage.priceVnd ?? 0, locale)}
                    </span>
                  </div>
                  <div className="mt-4 flex items-center gap-2">
                    <div className="bg-primary/10 flex h-8 items-center rounded-full px-4">
                      <span className="text-primary text-xs font-bold tracking-wider uppercase">
                        {t('billing.topup.packages.credits', {
                          credits: billingPackage.creditAmount ?? 0,
                        })}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex-1">
                  <div className="via-border mb-4 h-px w-full bg-linear-to-r from-transparent to-transparent" />
                  <p className="text-foreground mb-4 text-xs font-bold tracking-widest uppercase opacity-60">
                    {t('billing.topup.packages.includes')}
                  </p>
                  <ul className="space-y-4">
                    {billingPackage.includedFeatures.map((includedFeature) => (
                      <li key={includedFeature} className="flex items-start gap-3 text-sm">
                        <div className="bg-primary/10 mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full">
                          <Check className="text-primary h-3 w-3" aria-hidden="true" />
                        </div>
                        <span className="text-muted-foreground/90 leading-tight">
                          {includedFeature}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>

                <Button
                  type="button"
                  variant={isSelected ? 'default' : 'outline'}
                  size="lg"
                  className={cn(
                    'mt-8 h-12 w-full rounded-xl font-bold transition-all duration-300',
                    !isSelected &&
                      'hover:bg-primary hover:text-primary-foreground hover:border-primary',
                  )}
                  disabled={createIntent.isPending}
                  onClick={() => void submitSelectedPackage(billingPackage.code ?? '')}
                >
                  {isSubmitting ? (
                    <Loader2 className="mr-2 size-4 animate-spin" aria-hidden="true" />
                  ) : null}
                  {t('billing.topup.packages.cta')}
                </Button>
              </div>
            </article>
          );
        })}
      </div>

      {validationError ? (
        <Alert variant="warning" className="rounded-2xl border-orange-200 bg-orange-50/50">
          <AlertDescription className="text-orange-800">{validationError}</AlertDescription>
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
