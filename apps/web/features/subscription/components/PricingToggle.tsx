'use client';

import { useTranslations } from 'next-intl';

import { cn } from '@/lib/utils';

type PricingToggleProps = {
  isAnnual: boolean;
  onChange: (isAnnual: boolean) => void;
};

export function PricingToggle({ isAnnual, onChange }: PricingToggleProps) {
  const t = useTranslations();

  return (
    <div className="flex flex-col items-center justify-center gap-3">
      <div className="bg-muted/40 border-border/50 relative inline-flex items-center gap-1 rounded-full border p-1 backdrop-blur-xs">
        <button
          type="button"
          onClick={() => onChange(false)}
          className={cn(
            'focus-visible:ring-ring relative cursor-pointer rounded-full px-5 py-2 text-sm font-semibold transition-all duration-300 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none',
            !isAnnual
              ? 'bg-primary text-primary-foreground shadow-sm'
              : 'text-muted-foreground hover:text-foreground',
          )}
        >
          {t('subscription.monthly')}
        </button>

        <button
          type="button"
          onClick={() => onChange(true)}
          className={cn(
            'focus-visible:ring-ring relative cursor-pointer rounded-full px-5 py-2 text-sm font-semibold transition-all duration-300 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none',
            isAnnual
              ? 'bg-primary text-primary-foreground shadow-sm'
              : 'text-muted-foreground hover:text-foreground',
          )}
        >
          <span>{t('subscription.annual')}</span>
        </button>
      </div>

      <div className="bg-primary/10 border-primary/20 text-primary animate-bounce rounded-full border px-3 py-1 text-[11px] font-bold tracking-wider uppercase">
        {t('subscription.savePercent')}
      </div>
    </div>
  );
}
