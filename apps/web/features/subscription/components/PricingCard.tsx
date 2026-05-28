'use client';

import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { PricingFeatureList } from '@/features/subscription/components/PricingFeatureList';
import { cn } from '@/lib/utils';

type PricingCardProps = {
  name: string;
  description: string;
  price: string | number;
  isAnnual: boolean;
  isPopular?: boolean;
  features: string[];
  includesIntro?: string;
  ctaText: string;
  isCurrentPlan?: boolean;
  icon: string; // Emoji
};

export function PricingCard({
  name,
  description,
  price,
  isAnnual,
  isPopular = false,
  features,
  includesIntro,
  ctaText,
  isCurrentPlan = false,
  icon,
}: PricingCardProps) {
  const t = useTranslations();

  const handleCtaClick = () => {
    if (isCurrentPlan) {
      toast.info(t('subscription.currentPlan'));
      return;
    }
    toast.success(`${t('subscription.upgrade')} to ${name} plan - feature coming soon!`);
  };

  return (
    <article
      className={cn(
        'group bg-card/40 relative flex min-h-[550px] flex-col overflow-hidden rounded-[2rem] border p-8 shadow-sm backdrop-blur-sm transition-all duration-500 hover:-translate-y-2 hover:shadow-2xl',
        isPopular
          ? 'border-primary/60 ring-primary/20 ring-1'
          : 'border-border/50 hover:border-primary/30',
      )}
    >
      {/* Background Glow Effect */}
      <div
        className={cn(
          'absolute -top-24 -right-24 h-48 w-48 rounded-full blur-[80px] transition-all duration-500',
          isPopular
            ? 'bg-primary/10 group-hover:bg-primary/20'
            : 'bg-primary/5 group-hover:bg-primary/15',
        )}
      />

      <div className="relative flex flex-1 flex-col">
        {isPopular ? (
          <div className="bg-primary text-primary-foreground absolute top-0 right-0 rounded-full px-3 py-1 text-[11px] font-bold tracking-wider uppercase">
            {t('subscription.popular')}
          </div>
        ) : null}

        <div className="mb-6 space-y-3">
          <div className="flex items-center gap-3">
            <span className="text-3xl" role="img" aria-label={name}>
              {icon}
            </span>
            <h2 className="text-foreground text-2xl font-bold tracking-tight">{name}</h2>
          </div>
          <p className="text-muted-foreground min-h-[40px] text-sm leading-relaxed">
            {description}
          </p>
        </div>

        <div className="mb-8">
          <div className="flex items-baseline gap-1">
            <span className="text-foreground text-4xl font-extrabold tracking-tight">
              {typeof price === 'number' ? `$${price}` : price}
            </span>
            <span className="text-muted-foreground text-sm font-semibold">
              {t('subscription.perMonth')}
            </span>
          </div>
          {isAnnual && price !== '$0' && price !== 0 && (
            <p className="text-primary mt-1 text-xs font-semibold">
              {t('subscription.billedAnnually')}
            </p>
          )}
        </div>

        {/* Features list */}
        <PricingFeatureList features={features} includesIntro={includesIntro} />

        <Button
          type="button"
          variant={isPopular ? 'default' : 'outline'}
          size="lg"
          className={cn(
            'mt-8 h-12 w-full cursor-pointer rounded-xl font-bold transition-all duration-300',
            !isPopular &&
              !isCurrentPlan &&
              'hover:bg-primary hover:text-primary-foreground hover:border-primary',
            isCurrentPlan &&
              'bg-muted/80 text-muted-foreground hover:bg-muted/80 cursor-not-allowed',
          )}
          onClick={handleCtaClick}
          disabled={isCurrentPlan}
        >
          {isCurrentPlan ? t('subscription.currentPlan') : ctaText}
        </Button>
      </div>
    </article>
  );
}
