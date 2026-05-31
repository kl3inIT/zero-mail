'use client';

import { Check, Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { toast } from 'sonner';

import type { BillingPlanResponse } from '@/features/billing/api/billing-api';
import { useBillingPlans } from '@/features/billing/hooks/useBillingPlans';
import { useStartBillingCheckout } from '@/features/billing/hooks/useStartBillingCheckout';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';

const VND_NUMBER_FORMAT = new Intl.NumberFormat('vi-VN');

function formatVnd(value: number): string {
  return VND_NUMBER_FORMAT.format(value);
}

export function PlanList() {
  const t = useTranslations();
  const plansQuery = useBillingPlans();
  const checkoutMutation = useStartBillingCheckout();
  const [pendingPlanCode, setPendingPlanCode] = useState<BillingPlanResponse['code'] | null>(null);

  if (plansQuery.isLoading) {
    return (
      <div className="flex h-48 items-center justify-center">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  if (plansQuery.isError || !plansQuery.data) {
    return (
      <div className="text-muted-foreground rounded-md border p-6 text-center text-sm">
        {plansQuery.error instanceof Error ? plansQuery.error.message : t('billing.plans.error')}
      </div>
    );
  }

  const currentPlanCode = plansQuery.data.currentPlanCode;
  const plans = plansQuery.data.plans.toSorted((a, b) => a.tierRank - b.tierRank);
  const currentPlanTier = plans.find((plan) => plan.code === currentPlanCode)?.tierRank ?? 0;

  function startCheckout(plan: BillingPlanResponse): void {
    setPendingPlanCode(plan.code);
    checkoutMutation.mutate(plan.code, {
      onSuccess: (response) => window.location.assign(response.checkoutUrl),
      onError: () => toast.error(t('billing.plans.checkoutError')),
      onSettled: () => setPendingPlanCode(null),
    });
  }

  if (plans.length === 0) {
    return (
      <div className="text-muted-foreground rounded-md border p-6 text-center text-sm">
        {t('billing.plans.empty')}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
      {plans.map((plan) => (
        <PlanCard
          key={plan.code}
          plan={plan}
          isCurrent={plan.code === currentPlanCode}
          isLowerThanCurrent={plan.tierRank < currentPlanTier}
          isCheckoutPending={pendingPlanCode === plan.code && checkoutMutation.isPending}
          onStartCheckout={startCheckout}
        />
      ))}
    </div>
  );
}

interface PlanCardProps {
  plan: BillingPlanResponse;
  isCurrent: boolean;
  isLowerThanCurrent: boolean;
  isCheckoutPending: boolean;
  onStartCheckout: (plan: BillingPlanResponse) => void;
}

function PlanCard({
  plan,
  isCurrent,
  isLowerThanCurrent,
  isCheckoutPending,
  onStartCheckout,
}: PlanCardProps) {
  const t = useTranslations();
  const isFree = plan.code === 'FREE';
  const isFeatured = plan.code === 'PLUS';

  const displayName = (() => {
    switch (plan.code) {
      case 'FREE':
        return t('billing.plans.plan.free');
      case 'PLUS':
        return t('billing.plans.plan.plus');
      case 'PRO':
        return t('billing.plans.plan.pro');
      default:
        return plan.displayName;
    }
  })();

  const description = (() => {
    switch (plan.code) {
      case 'FREE':
        return t('billing.plans.planDescription.free');
      case 'PLUS':
        return t('billing.plans.planDescription.plus');
      case 'PRO':
        return t('billing.plans.planDescription.pro');
      default:
        return null;
    }
  })();

  const ctaLabel = (() => {
    if (isCurrent) return t('billing.plans.currentPlan');
    if (isLowerThanCurrent) return t('billing.plans.lowerTierUnavailable');
    if (isCheckoutPending) return t('billing.plans.checkoutPending');
    if (isFree) return t('billing.plans.freeIncluded');
    return t('billing.plans.checkoutCta');
  })();
  const isCheckoutDisabled = isCurrent || isFree || isLowerThanCurrent || isCheckoutPending;

  return (
    <Card
      className={cn(
        'relative flex h-full flex-col transition-shadow',
        isCurrent && 'border-primary ring-primary/40 shadow-lg ring-2',
        !isCurrent && isFeatured && 'border-primary/60 ring-primary/20 shadow-md ring-1',
      )}
    >
      {isCurrent && (
        <div className="bg-primary text-primary-foreground absolute top-4 right-4 rounded-full px-3 py-1 text-xs font-semibold">
          {t('billing.plans.currentPlan')}
        </div>
      )}
      <CardHeader className={isCurrent ? 'pr-28' : undefined}>
        <CardTitle className="text-2xl">{displayName}</CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-6">
        <div>
          <div className="flex items-baseline gap-1">
            <span className="text-foreground text-4xl font-bold tracking-tight">
              {isFree ? '0₫' : `${formatVnd(plan.priceVnd)}₫`}
            </span>
            {!isFree && (
              <span className="text-muted-foreground text-sm">{t('billing.plans.perMonth')}</span>
            )}
          </div>
          <p className="text-muted-foreground mt-2 text-sm">
            {plan.monthlyCreditAllowance.toLocaleString('vi-VN')} {t('billing.balance.unit')} /{' '}
            {t('billing.plans.monthly').toLowerCase()}
          </p>
        </div>

        <ul className="text-foreground space-y-2 text-sm">
          <li className="flex items-start gap-2">
            <Check className="text-primary mt-0.5 size-4 shrink-0" />
            <span>
              {t('billing.plans.includedCredits', {
                credits: plan.monthlyCreditAllowance.toLocaleString('vi-VN'),
              })}
            </span>
          </li>
          {plan.features.map((feature) => (
            <li key={feature.code} className="flex items-start gap-2">
              <Check className="text-primary mt-0.5 size-4 shrink-0" />
              <div className="flex flex-col">
                <span>{feature.displayName}</span>
                {feature.creditCost > 0 && (
                  <span className="text-muted-foreground text-xs">
                    {feature.creditCost} {t('billing.balance.unit')}{' '}
                    {t('billing.plans.perInvocation')}
                  </span>
                )}
              </div>
            </li>
          ))}
        </ul>

        <div className="mt-auto">
          <Button
            type="button"
            variant={
              isCurrent || isLowerThanCurrent ? 'outline' : isFeatured ? 'default' : 'outline'
            }
            className="w-full"
            disabled={isCheckoutDisabled}
            onClick={() => onStartCheckout(plan)}
          >
            {isCheckoutPending && <Loader2 className="size-4 animate-spin" />}
            {ctaLabel}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
