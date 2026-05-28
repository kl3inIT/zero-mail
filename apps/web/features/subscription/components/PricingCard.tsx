'use client';

import { ChevronDown } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState, useRef, useEffect } from 'react';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export type CreditOption = {
  creditsValue: number;
  priceMonthly: number;
  priceAnnual: number;
  labelEn: string;
  labelVi: string;
};

type PricingCardProps = {
  planName: string;
  isFeatured?: boolean;
  basePriceMonthly?: number;
  basePriceAnnual?: number;
  fixedCreditsString?: string;
  creditOptions?: CreditOption[];
  selectedCreditsValue?: number;
  onCreditsValueChange?: (credits: number) => void;
  isAnnualBillingSelected: boolean;
  onCtaClick: () => void;
  isCurrentPlanActive?: boolean;
  featuresTriage: string[];
  featuresLimits: string[];
};

export function PricingCard({
  planName,
  isFeatured = false,
  basePriceMonthly,
  basePriceAnnual,
  fixedCreditsString,
  creditOptions = [],
  selectedCreditsValue,
  onCreditsValueChange,
  isAnnualBillingSelected,
  onCtaClick,
  isCurrentPlanActive = false,
  featuresTriage,
  featuresLimits,
}: PricingCardProps) {
  const t = useTranslations();
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // Determine current active option and price
  let displayPrice = 0;
  let activeCreditsString = fixedCreditsString || '';

  const hasCreditDropdown = creditOptions.length > 0;
  const currentActiveOption = creditOptions.find(
    (option) => option.creditsValue === selectedCreditsValue,
  );

  if (hasCreditDropdown && currentActiveOption) {
    displayPrice = isAnnualBillingSelected
      ? currentActiveOption.priceAnnual
      : currentActiveOption.priceMonthly;
    activeCreditsString = `${selectedCreditsValue?.toLocaleString()}`;
  } else {
    displayPrice = isAnnualBillingSelected ? (basePriceAnnual ?? 0) : (basePriceMonthly ?? 0);
  }

  const handleDropdownSelect = (credits: number) => {
    if (onCreditsValueChange) {
      onCreditsValueChange(credits);
    }
    setIsDropdownOpen(false);
  };

  return (
    <article
      className={cn(
        'text-foreground bg-card text-card-foreground relative flex flex-col rounded-[24px] border p-6 transition-all duration-300 select-none',
        isFeatured
          ? 'border-blue-600 shadow-[0_0_30px_rgba(37,99,235,0.08)] ring-1 ring-blue-500'
          : 'border-border hover:border-zinc-400 dark:hover:border-zinc-700',
      )}
    >
      {/* Glow Effect for Featured Card */}
      {isFeatured && (
        <div className="pointer-events-none absolute inset-0 -z-10 rounded-[24px] bg-blue-500/5 opacity-50 blur-xl dark:bg-blue-500/3" />
      )}

      {/* Plan Name & Pricing Header */}
      <div className="mb-6 space-y-2">
        <h3 className="text-foreground text-xl font-bold tracking-tight">{planName}</h3>
        <div className="flex items-baseline gap-1">
          <span className="text-foreground text-4xl font-extrabold tracking-tight">
            ${displayPrice.toFixed(2)}
          </span>
          <span className="text-muted-foreground text-sm font-medium">
            {t('subscription.perMonth')}
          </span>
        </div>
        {isAnnualBillingSelected && (
          <p className="text-xs font-semibold text-blue-500 dark:text-blue-400">
            {t('subscription.billedAnnually')}
          </p>
        )}
      </div>

      {/* Credit Dropdown Select or Spacing */}
      <div className="relative mb-6" ref={dropdownRef}>
        {hasCreditDropdown && currentActiveOption ? (
          <>
            <button
              type="button"
              onClick={() => setIsDropdownOpen(!isDropdownOpen)}
              className="bg-muted hover:bg-muted/80 text-foreground border-input flex w-full cursor-pointer items-center justify-between rounded-xl border px-4 py-3 text-sm font-medium transition-all"
            >
              <span>
                {t('subscription.section.credits') === 'Credits'
                  ? currentActiveOption.labelEn
                  : currentActiveOption.labelVi}
              </span>
              <ChevronDown
                className={cn(
                  'text-muted-foreground h-4 w-4 transition-transform duration-300',
                  isDropdownOpen && 'rotate-180',
                )}
              />
            </button>

            {isDropdownOpen && (
              <div className="bg-popover border-border absolute top-full right-0 left-0 z-50 mt-1 overflow-hidden rounded-xl border shadow-2xl">
                {creditOptions.map((option) => (
                  <button
                    key={option.creditsValue}
                    type="button"
                    onClick={() => handleDropdownSelect(option.creditsValue)}
                    className={cn(
                      'hover:bg-muted w-full cursor-pointer px-4 py-3 text-left text-sm transition-all',
                      option.creditsValue === selectedCreditsValue
                        ? 'bg-blue-500/10 font-semibold text-blue-500'
                        : 'text-foreground',
                    )}
                  >
                    {t('subscription.section.credits') === 'Credits'
                      ? option.labelEn
                      : option.labelVi}
                  </button>
                ))}
              </div>
            )}
          </>
        ) : (
          <div className="h-11" /> // Visual spacer to align cards perfectly when no dropdown is present
        )}
      </div>

      {/* Action Button */}
      <Button
        type="button"
        disabled={isCurrentPlanActive}
        onClick={onCtaClick}
        className={cn(
          'w-full cursor-pointer rounded-xl border-none py-6 text-sm font-bold shadow-none transition-all duration-300',
          isCurrentPlanActive
            ? 'cursor-not-allowed bg-zinc-800 text-zinc-500 dark:bg-zinc-900 dark:text-zinc-600'
            : isFeatured
              ? 'bg-blue-600 text-white shadow-[0_0_20px_rgba(37,99,235,0.25)] hover:bg-blue-500'
              : 'bg-zinc-200 text-zinc-950 hover:bg-zinc-300 dark:bg-zinc-800 dark:text-zinc-100 dark:hover:bg-zinc-700',
        )}
      >
        {isCurrentPlanActive ? t('subscription.currentPlan') : t('subscription.toStart')}
      </Button>

      {/* Divider */}
      <div className="bg-border my-6 h-[1px]" />

      {/* Feature Sections */}
      <div className="flex flex-1 flex-col justify-start space-y-6">
        {/* Credits Section */}
        <div className="space-y-1.5">
          <h4 className="text-muted-foreground text-[11px] font-bold tracking-wider uppercase">
            {t('subscription.section.credits')}
          </h4>
          <p className="text-foreground text-sm leading-relaxed font-medium">
            {t('subscription.section.credits') === 'Credits'
              ? `${activeCreditsString} credits/month`
              : `${activeCreditsString} tín dụng/tháng`}
          </p>
        </div>

        {/* Divider */}
        <div className="bg-border h-[1px]" />

        {/* AI Email Automation Section */}
        <div className="space-y-3">
          <h4 className="text-muted-foreground text-[11px] font-bold tracking-wider uppercase">
            {t('subscription.section.triage')}
          </h4>
          <ul className="text-foreground/80 space-y-2 text-[13px] leading-relaxed font-medium">
            {featuresTriage.map((feature, idx) => (
              <li key={idx} className="flex items-start">
                <span className="mr-2 text-blue-500">•</span>
                <span>{feature}</span>
              </li>
            ))}
          </ul>
        </div>

        {/* Divider */}
        <div className="bg-border h-[1px]" />

        {/* Limits & Support Section */}
        <div className="space-y-3">
          <h4 className="text-muted-foreground text-[11px] font-bold tracking-wider uppercase">
            {t('subscription.section.limits')}
          </h4>
          <ul className="text-foreground/80 space-y-2 text-[13px] leading-relaxed font-medium">
            {featuresLimits.map((feature, idx) => (
              <li key={idx} className="flex items-start">
                <span className="mr-2 text-blue-500">•</span>
                <span>{feature}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </article>
  );
}
export default PricingCard;
