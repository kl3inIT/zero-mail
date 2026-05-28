'use client';

import { X } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { toast } from 'sonner';

import { PricingCard, CreditOption } from '@/features/subscription/components/PricingCard';
import { cn } from '@/lib/utils';

export function SubscriptionPage() {
  const t = useTranslations();
  const pricingDialogRouter = useRouter();
  const [isAnnualBillingSelected, setIsAnnualBillingSelected] = useState(false);

  // Column 2 credit options configuration
  const columnTwoPricingOptions: CreditOption[] = [
    {
      creditsValue: 21000,
      priceMonthly: 49.99,
      priceAnnual: 39.99,
      labelEn: '21,000 credits / month',
      labelVi: '21.000 tín dụng / tháng',
    },
    {
      creditsValue: 45000,
      priceMonthly: 99.99,
      priceAnnual: 79.99,
      labelEn: '45,000 credits / month',
      labelVi: '45.000 tín dụng / tháng',
    },
    {
      creditsValue: 70000,
      priceMonthly: 149.99,
      priceAnnual: 119.99,
      labelEn: '70,000 credits / month',
      labelVi: '70.000 tín dụng / tháng',
    },
  ];

  // Column 3 credit options configuration
  const columnThreePricingOptions: CreditOption[] = [
    {
      creditsValue: 125000,
      priceMonthly: 249.99,
      priceAnnual: 199.99,
      labelEn: '125,000 credits / month',
      labelVi: '125.000 tín dụng / tháng',
    },
    {
      creditsValue: 250000,
      priceMonthly: 449.99,
      priceAnnual: 359.99,
      labelEn: '250,000 credits / month',
      labelVi: '250.000 tín dụng / tháng',
    },
    {
      creditsValue: 500000,
      priceMonthly: 799.99,
      priceAnnual: 639.99,
      labelEn: '500,000 credits / month',
      labelVi: '500.000 tín dụng / tháng',
    },
  ];

  // Dynamic state values for dropdown selection
  const [columnTwoSelectedCredits, setColumnTwoSelectedCredits] = useState<number>(21000);
  const [columnThreeSelectedCredits, setColumnThreeSelectedCredits] = useState<number>(125000);

  const handlePricingDialogClose = () => {
    pricingDialogRouter.back();
  };

  const handlePricingCtaAction = (planTitle: string, credits: number, pricePerMonth: number) => {
    const formattedPrice = pricePerMonth.toFixed(2);
    const billingType = isAnnualBillingSelected ? 'annually' : 'monthly';
    const localizedMessage =
      t('subscription.section.credits') === 'Credits'
        ? `Upgraded to ${planTitle} (${credits.toLocaleString()} credits) at $${formattedPrice}/mo billed ${billingType}!`
        : `Đã nâng cấp lên ${planTitle} (${credits.toLocaleString()} tín dụng) với $${formattedPrice}/tháng thanh toán ${
            billingType === 'annually' ? 'hàng năm' : 'hàng tháng'
          }!`;
    toast.success(localizedMessage);
  };

  // Structured customized lists of features for Zero Mail (AI triage, auto-drafting, custom rules)
  const starterTriageFeatures = [
    t('subscription.feature.triage.basic'),
    t('subscription.feature.drafts.basic'),
    t('subscription.feature.rules.basic'),
  ];
  const starterLimitsFeatures = [
    t('subscription.feature.limits.1gmail'),
    t('subscription.feature.support.basic'),
  ];

  const plusTriageFeatures = [
    t('subscription.feature.triage.advanced'),
    t('subscription.feature.drafts.high'),
    t('subscription.feature.rules.advanced'),
  ];
  const plusLimitsFeatures = [
    t('subscription.feature.limits.3gmail'),
    t('subscription.feature.support.priority'),
  ];

  const proTriageFeatures = [
    t('subscription.feature.triage.enterprise'),
    t('subscription.feature.drafts.unlimited'),
    t('subscription.feature.rules.api'),
  ];
  const proLimitsFeatures = [
    t('subscription.feature.limits.unlimited'),
    t('subscription.feature.support.dedicated'),
  ];

  return (
    <div className="bg-background flex min-h-screen items-center justify-center px-4 py-8 transition-colors duration-300 md:px-8">
      {/* Premium Dialog Container */}
      <section className="border-border bg-card text-card-foreground relative w-full max-w-6xl rounded-[28px] border p-6 shadow-2xl transition-colors duration-300 md:p-10">
        {/* Close Button X */}
        <button
          type="button"
          onClick={handlePricingDialogClose}
          className="text-muted-foreground hover:text-foreground absolute top-6 right-6 cursor-pointer transition-colors"
          aria-label="Close pricing portal"
        >
          <X className="h-6 w-6" />
        </button>

        {/* Modal Header */}
        <div className="mb-8 text-center">
          <h1 className="text-foreground text-3xl font-extrabold tracking-tight md:text-4xl">
            {t('subscription.title')}
          </h1>
        </div>

        {/* Billing Schedule Toggle Container */}
        <div className="mb-10 flex justify-center">
          <div className="bg-muted border-border inline-flex rounded-full border p-1.5 transition-colors duration-300">
            <button
              type="button"
              onClick={() => setIsAnnualBillingSelected(false)}
              className={cn(
                'cursor-pointer rounded-full px-5 py-2.5 text-sm font-semibold transition-all duration-300',
                !isAnnualBillingSelected
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {t('subscription.monthly')}
            </button>

            <button
              type="button"
              onClick={() => setIsAnnualBillingSelected(true)}
              className={cn(
                'flex cursor-pointer items-center gap-1.5 rounded-full px-5 py-2.5 text-sm font-semibold transition-all duration-300',
                isAnnualBillingSelected
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              <span>{t('subscription.annual')}</span>
              <span className="text-[11px] font-extrabold tracking-wider text-blue-500 dark:text-blue-400">
                {t('subscription.savePercent')}
              </span>
            </button>
          </div>
        </div>

        {/* Dynamic 3-Column Plan Grid */}
        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {/* Plan 1: Starter */}
          <PricingCard
            planName={t('subscription.plan.starter')}
            isFeatured={false}
            basePriceMonthly={24.99}
            basePriceAnnual={19.99}
            fixedCreditsString="10,000"
            isAnnualBillingSelected={isAnnualBillingSelected}
            featuresTriage={starterTriageFeatures}
            featuresLimits={starterLimitsFeatures}
            onCtaClick={() =>
              handlePricingCtaAction(
                t('subscription.plan.starter'),
                10000,
                isAnnualBillingSelected ? 19.99 : 24.99,
              )
            }
          />

          {/* Plan 2: Plus */}
          <PricingCard
            planName={t('subscription.plan.plus')}
            isFeatured={true}
            creditOptions={columnTwoPricingOptions}
            selectedCreditsValue={columnTwoSelectedCredits}
            onCreditsValueChange={setColumnTwoSelectedCredits}
            isAnnualBillingSelected={isAnnualBillingSelected}
            featuresTriage={plusTriageFeatures}
            featuresLimits={plusLimitsFeatures}
            onCtaClick={() => {
              const activeOption = columnTwoPricingOptions.find(
                (opt) => opt.creditsValue === columnTwoSelectedCredits,
              );
              if (activeOption) {
                handlePricingCtaAction(
                  t('subscription.plan.plus'),
                  columnTwoSelectedCredits,
                  isAnnualBillingSelected ? activeOption.priceAnnual : activeOption.priceMonthly,
                );
              }
            }}
          />

          {/* Plan 3: Pro */}
          <PricingCard
            planName={t('subscription.plan.pro')}
            isFeatured={false}
            creditOptions={columnThreePricingOptions}
            selectedCreditsValue={columnThreeSelectedCredits}
            onCreditsValueChange={setColumnThreeSelectedCredits}
            isAnnualBillingSelected={isAnnualBillingSelected}
            featuresTriage={proTriageFeatures}
            featuresLimits={proLimitsFeatures}
            onCtaClick={() => {
              const activeOption = columnThreePricingOptions.find(
                (opt) => opt.creditsValue === columnThreeSelectedCredits,
              );
              if (activeOption) {
                handlePricingCtaAction(
                  t('subscription.plan.pro'),
                  columnThreeSelectedCredits,
                  isAnnualBillingSelected ? activeOption.priceAnnual : activeOption.priceMonthly,
                );
              }
            }}
          />
        </div>
      </section>
    </div>
  );
}
export default SubscriptionPage;
