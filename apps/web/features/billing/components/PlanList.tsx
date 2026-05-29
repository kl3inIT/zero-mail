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

function formatVnd(value: number): string {
  return new Intl.NumberFormat('vi-VN').format(value);
}

export function PlanList() {
  const plansQuery = useBillingPlans();
  const checkoutMutation = useStartBillingCheckout();
  const [pendingPlanCode, setPendingPlanCode] = useState<BillingPlanResponse['code'] | null>(null);

  if (plansQuery.isLoading) {
    return (
      <div className="flex h-48 items-center justify-center">
        <Loader2 className="text-muted-foreground h-6 w-6 animate-spin" />
      </div>
    );
  }

  if (plansQuery.isError || !plansQuery.data) {
    return (
      <div className="text-muted-foreground rounded-md border p-6 text-center text-sm">
        {plansQuery.error instanceof Error ? plansQuery.error.message : 'Failed to load plans.'}
      </div>
    );
  }

  const currentPlanCode = plansQuery.data.currentPlanCode;
  const plans = [...plansQuery.data.plans].sort((a, b) => a.tierRank - b.tierRank);

  function startCheckout(plan: BillingPlanResponse): void {
    setPendingPlanCode(plan.code);
    checkoutMutation.mutate(plan.code, {
      onSuccess: (response) => window.location.assign(response.checkoutUrl),
      onError: () => toast.error('Không thể mở trang thanh toán. Vui lòng thử lại sau.'),
      onSettled: () => setPendingPlanCode(null),
    });
  }

  if (plans.length === 0) {
    return (
      <div className="text-muted-foreground rounded-md border p-6 text-center text-sm">
        Không có gói nào để hiển thị.
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
  isCheckoutPending: boolean;
  onStartCheckout: (plan: BillingPlanResponse) => void;
}

function PlanCard({ plan, isCurrent, isCheckoutPending, onStartCheckout }: PlanCardProps) {
  const t = useTranslations();
  const isFree = plan.code === 'FREE';
  const isFeatured = plan.code === 'PLUS';

  const displayName = (() => {
    switch (plan.code) {
      case 'FREE':
        return t('subscription.free.name');
      case 'PLUS':
        return t('subscription.plan.plus');
      case 'PRO':
        return t('subscription.plan.pro');
      default:
        return plan.displayName;
    }
  })();

  const description = (() => {
    switch (plan.code) {
      case 'FREE':
        return t('subscription.free.description');
      case 'PLUS':
        return 'Cho người dùng cá nhân muốn dùng AI thường xuyên hơn.';
      case 'PRO':
        return 'Cho người dùng nâng cao và team nhỏ.';
      default:
        return null;
    }
  })();

  const ctaLabel = (() => {
    if (isCurrent) return t('subscription.currentPlan');
    if (isCheckoutPending) return 'Đang mở checkout';
    if (isFree) return t('subscription.getStarted');
    return 'Nâng cấp ngay';
  })();

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
          {t('subscription.currentPlan')}
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
              <span className="text-muted-foreground text-sm">{t('subscription.perMonth')}</span>
            )}
          </div>
          <p className="text-muted-foreground mt-2 text-sm">
            {plan.monthlyCreditAllowance.toLocaleString('vi-VN')} {t('billing.balance.unit')} /{' '}
            {t('subscription.monthly').toLowerCase()}
          </p>
        </div>

        <ul className="text-foreground space-y-2 text-sm">
          <li className="flex items-start gap-2">
            <Check className="text-primary mt-0.5 h-4 w-4 shrink-0" />
            <span>
              {plan.monthlyCreditAllowance.toLocaleString('vi-VN')} {t('billing.balance.unit')} mỗi
              tháng
            </span>
          </li>
          {plan.features.map((feature) => (
            <li key={feature.code} className="flex items-start gap-2">
              <Check className="text-primary mt-0.5 h-4 w-4 shrink-0" />
              <div className="flex flex-col">
                <span>{feature.displayName}</span>
                {feature.creditCost > 0 && (
                  <span className="text-muted-foreground text-xs">
                    {feature.creditCost} {t('billing.balance.unit')} / lượt
                  </span>
                )}
              </div>
            </li>
          ))}
        </ul>

        <div className="mt-auto">
          <Button
            type="button"
            variant={isCurrent ? 'outline' : isFeatured ? 'default' : 'outline'}
            className="w-full"
            disabled={isCurrent || isFree || isCheckoutPending}
            onClick={() => onStartCheckout(plan)}
          >
            {isCheckoutPending && <Loader2 className="h-4 w-4 animate-spin" />}
            {ctaLabel}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
