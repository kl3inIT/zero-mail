'use client';

import { useQueryClient } from '@tanstack/react-query';
import { Check, Copy, CreditCard, Loader2, QrCode } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';

import type {
  BankTransferIntentResponse,
  BillingCheckoutRequest,
  BillingPaymentMethod,
  BillingPlanResponse,
} from '@/features/billing/api/billing-api';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { billingKeys } from '@/features/billing/query-keys';
import { useBillingPlans } from '@/features/billing/hooks/useBillingPlans';
import { usePlanUpgradePaymentWebSocket } from '@/features/billing/hooks/usePlanUpgradePaymentWebSocket';
import { useStartBillingCheckout } from '@/features/billing/hooks/useStartBillingCheckout';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { cn } from '@/lib/utils';

const VND_NUMBER_FORMAT = new Intl.NumberFormat('vi-VN');

function formatVnd(value: number): string {
  return VND_NUMBER_FORMAT.format(value);
}

export function PlanList() {
  const t = useTranslations();
  const router = useRouter();
  const queryClient = useQueryClient();
  const currentUserQuery = useCurrentUser();
  const plansQuery = useBillingPlans();
  const checkoutMutation = useStartBillingCheckout();
  const [pendingPayment, setPendingPayment] = useState<PendingPayment | null>(null);
  const [bankTransferIntent, setBankTransferIntent] = useState<BankTransferIntentResponse | null>(
    null,
  );

  usePlanUpgradePaymentWebSocket({
    tenantId: currentUserQuery.data?.tenantId,
    bankTransferIntentId: bankTransferIntent?.id,
    bankTransferCode: bankTransferIntent?.code,
    enabled: bankTransferIntent !== null,
    onPaymentCompleted: () => {
      setBankTransferIntent(null);
      toast.success(t('billing.bankTransfer.paymentSuccess'));
      void queryClient.invalidateQueries({ queryKey: billingKeys.all });
      router.replace('/credits');
    },
  });

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

  function startCheckout(plan: BillingPlanResponse, paymentMethod: BillingPaymentMethod): void {
    if (plan.code === 'FREE') {
      return;
    }
    const checkoutRequest: BillingCheckoutRequest = { planCode: plan.code, paymentMethod };
    setPendingPayment({ planCode: checkoutRequest.planCode, paymentMethod });
    checkoutMutation.mutate(checkoutRequest, {
      onSuccess: (response) => {
        if (response.paymentMethod === 'LEMON_SQUEEZY' && response.checkoutUrl) {
          window.location.assign(response.checkoutUrl);
          return;
        }
        if (response.paymentMethod === 'SEPAY_BANK_TRANSFER' && response.bankTransferIntent) {
          setBankTransferIntent(response.bankTransferIntent);
          return;
        }
        toast.error(t('billing.plans.checkoutError'));
      },
      onError: () => toast.error(t('billing.plans.checkoutError')),
      onSettled: () => setPendingPayment(null),
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
    <div className="space-y-6">
      <Dialog
        open={bankTransferIntent !== null}
        onOpenChange={(nextOpen) => {
          if (!nextOpen) {
            setBankTransferIntent(null);
          }
        }}
      >
        {bankTransferIntent && (
          <BankTransferDialog
            intent={bankTransferIntent}
            plan={plans.find((plan) => plan.code === bankTransferIntent.planCode)}
          />
        )}
      </Dialog>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        {plans.map((plan) => (
          <PlanCard
            key={plan.code}
            plan={plan}
            isCurrent={plan.code === currentPlanCode}
            isLowerThanCurrent={plan.tierRank < currentPlanTier}
            pendingPayment={pendingPayment}
            isCheckoutPending={pendingPayment?.planCode === plan.code && checkoutMutation.isPending}
            onStartCheckout={startCheckout}
          />
        ))}
      </div>
    </div>
  );
}

type PendingPayment = {
  planCode: BillingCheckoutRequest['planCode'];
  paymentMethod: BillingPaymentMethod;
};

interface PlanCardProps {
  plan: BillingPlanResponse;
  isCurrent: boolean;
  isLowerThanCurrent: boolean;
  pendingPayment: PendingPayment | null;
  isCheckoutPending: boolean;
  onStartCheckout: (plan: BillingPlanResponse, paymentMethod: BillingPaymentMethod) => void;
}

function PlanCard({
  plan,
  isCurrent,
  isLowerThanCurrent,
  pendingPayment,
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
  const isLemonPending =
    pendingPayment?.planCode === plan.code &&
    pendingPayment.paymentMethod === 'LEMON_SQUEEZY' &&
    isCheckoutPending;
  const isSepayPending =
    pendingPayment?.planCode === plan.code &&
    pendingPayment.paymentMethod === 'SEPAY_BANK_TRANSFER' &&
    isCheckoutPending;

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

        <div className="mt-auto space-y-2">
          {isCheckoutDisabled ? (
            <Button
              type="button"
              variant={
                isCurrent || isLowerThanCurrent ? 'outline' : isFeatured ? 'default' : 'outline'
              }
              className="w-full"
              disabled
            >
              {isCheckoutPending && <Loader2 className="size-4 animate-spin" />}
              {ctaLabel}
            </Button>
          ) : (
            <>
              <Button
                type="button"
                variant={isFeatured ? 'default' : 'outline'}
                className="w-full"
                disabled={isCheckoutPending}
                onClick={() => onStartCheckout(plan, 'LEMON_SQUEEZY')}
              >
                {isLemonPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <CreditCard className="size-4" />
                )}
                {isLemonPending
                  ? t('billing.plans.checkoutPending')
                  : t('billing.plans.cardPayment')}
              </Button>
              <Button
                type="button"
                variant="outline"
                className="w-full"
                disabled={isCheckoutPending}
                onClick={() => onStartCheckout(plan, 'SEPAY_BANK_TRANSFER')}
              >
                {isSepayPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <QrCode className="size-4" />
                )}
                {isSepayPending
                  ? t('billing.plans.bankTransferPending')
                  : t('billing.plans.bankTransferPayment')}
              </Button>
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function BankTransferDialog({
  intent,
  plan,
}: {
  intent: BankTransferIntentResponse;
  plan?: BillingPlanResponse;
}) {
  const t = useTranslations();
  const locale = useLocale();
  const expiresAt = new Intl.DateTimeFormat(locale, {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(intent.expiresAt));
  const bankSummary = [intent.bankName ?? intent.bankCode, intent.accountNumber, intent.accountName]
    .filter(Boolean)
    .join(' · ');
  const planName = (() => {
    if (!plan) return intent.planCode;
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

  async function copyTransferContent(): Promise<void> {
    try {
      await navigator.clipboard.writeText(intent.transferContent);
      toast.success(t('billing.copy.done'));
    } catch {
      toast.error(t('billing.copy.failed'));
    }
  }

  return (
    <DialogContent className="max-h-[calc(100vh-2rem)] gap-5 overflow-y-auto p-5 sm:max-w-[380px] lg:max-w-[760px] lg:p-6">
      <DialogHeader className="items-center text-center lg:items-start lg:text-left">
        <div className="bg-primary/10 text-primary flex size-11 items-center justify-center rounded-full">
          <QrCode className="size-5" />
        </div>
        <DialogTitle>{t('billing.bankTransfer.title')}</DialogTitle>
        <DialogDescription>{t('billing.bankTransfer.shortDescription')}</DialogDescription>
      </DialogHeader>

      <div className="grid gap-5 lg:grid-cols-[360px_minmax(0,1fr)] lg:items-stretch">
        <div className="bg-background rounded-2xl border p-4 shadow-sm">
          <div className="mx-auto flex justify-center rounded-2xl bg-white p-3">
            {/* eslint-disable-next-line @next/next/no-img-element -- QR URL is generated by SE Pay per intent. */}
            <img
              src={intent.qrUrl}
              alt={t('billing.bankTransfer.qrAlt')}
              className="size-56 rounded-xl object-contain lg:size-64"
            />
          </div>

          <div className="mt-4 space-y-3">
            <div className="rounded-xl border p-3">
              <div className="mb-2 flex items-center justify-between gap-3">
                <p className="text-muted-foreground text-xs font-medium">
                  {t('billing.bankTransfer.content')}
                </p>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="h-7 px-2 text-xs"
                  onClick={() => void copyTransferContent()}
                >
                  <Copy className="size-3.5" />
                  {t('billing.bankTransfer.copyContent')}
                </Button>
              </div>
              <code className="block text-center font-mono text-base font-semibold break-all">
                {intent.transferContent}
              </code>
            </div>

            <div className="text-muted-foreground space-y-1 text-center text-xs">
              <p>{bankSummary}</p>
              <p>{t('billing.bankTransfer.expiresAtShort', { time: expiresAt })}</p>
            </div>
          </div>
        </div>

        <div className="bg-muted/30 flex flex-col rounded-2xl border p-4">
          <div>
            <p className="text-muted-foreground text-xs font-medium">
              {t('billing.bankTransfer.planLabel')}
            </p>
            <h3 className="mt-1 text-2xl font-semibold">{planName}</h3>
            {plan && (
              <p className="text-muted-foreground mt-2 text-sm">
                {t('billing.plans.includedCredits', {
                  credits: plan.monthlyCreditAllowance.toLocaleString('vi-VN'),
                })}
              </p>
            )}
          </div>

          <div className="mt-5 space-y-3">
            <PaymentSummaryRow
              label={t('billing.bankTransfer.amount')}
              value={`${formatVnd(intent.amountVnd)}₫`}
              valueClassName="text-xl font-semibold"
            />
            {plan && (
              <PaymentSummaryRow
                label={t('billing.bankTransfer.planPrice')}
                value={`${formatVnd(plan.priceVnd)}₫`}
              />
            )}
            <PaymentSummaryRow label={t('billing.bankTransfer.bank')} value={intent.bankCode} />
            <PaymentSummaryRow
              label={t('billing.bankTransfer.accountNumber')}
              value={intent.accountNumber}
            />
          </div>

          <div className="text-muted-foreground mt-auto pt-5 text-sm">
            {t('billing.bankTransfer.pendingNote')}
          </div>
        </div>
      </div>
    </DialogContent>
  );
}

function PaymentSummaryRow({
  label,
  value,
  valueClassName,
}: {
  label: string;
  value: string;
  valueClassName?: string;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b pb-3 last:border-b-0 last:pb-0">
      <span className="text-muted-foreground text-sm">{label}</span>
      <span className={cn('text-right text-sm font-medium break-all', valueClassName)}>
        {value}
      </span>
    </div>
  );
}
