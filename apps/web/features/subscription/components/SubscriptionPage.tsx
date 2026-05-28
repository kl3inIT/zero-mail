'use client';

import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { PricingCard } from '@/features/subscription/components/PricingCard';
import { PricingToggle } from '@/features/subscription/components/PricingToggle';

export function SubscriptionPage() {
  const t = useTranslations();
  const [isAnnual, setIsAnnual] = useState(false);

  const freeFeatures = [
    t('subscription.free.b1'),
    t('subscription.free.b2'),
    t('subscription.free.b3'),
    t('subscription.free.b4'),
    t('subscription.free.b5'),
  ];

  const plusFeatures = [
    t('subscription.plus.b1'),
    t('subscription.plus.b2'),
    t('subscription.plus.b3'),
    t('subscription.plus.b4'),
    t('subscription.plus.b5'),
    t('subscription.plus.b6'),
  ];

  const proFeatures = [
    t('subscription.pro.b1'),
    t('subscription.pro.b2'),
    t('subscription.pro.b3'),
    t('subscription.pro.b4'),
    t('subscription.pro.b5'),
  ];

  // Pricing values
  const freePrice = 0;
  const plusPrice = isAnnual ? 22.4 : 28;
  const proPrice = isAnnual ? 33.6 : 42;

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
      {/* Header section */}
      <div className="mb-12 space-y-4 text-center">
        <h1 className="text-foreground text-4xl font-extrabold tracking-tight sm:text-5xl">
          {t('subscription.title')}
        </h1>
        <p className="text-muted-foreground mx-auto max-w-2xl text-lg leading-relaxed">
          {t('subscription.subtitle')}
        </p>
      </div>

      {/* Monthly / Annual Toggle */}
      <div className="mb-16 flex justify-center">
        <PricingToggle isAnnual={isAnnual} onChange={setIsAnnual} />
      </div>

      {/* 3-Column Grid */}
      <div className="grid items-stretch gap-8 md:grid-cols-3">
        <PricingCard
          name={t('subscription.free.name')}
          description={t('subscription.free.description')}
          price={freePrice}
          isAnnual={isAnnual}
          features={freeFeatures}
          ctaText={t('subscription.getStarted')}
          isCurrentPlan={true}
          icon="💡"
        />

        <PricingCard
          name={t('subscription.plus.name')}
          description={t('subscription.plus.description')}
          price={plusPrice}
          isAnnual={isAnnual}
          isPopular={true}
          features={plusFeatures}
          includesIntro={t('subscription.plus.includesIntro')}
          ctaText={t('subscription.upgrade')}
          icon="⚡"
        />

        <PricingCard
          name={t('subscription.pro.name')}
          description={t('subscription.pro.description')}
          price={proPrice}
          isAnnual={isAnnual}
          features={proFeatures}
          includesIntro={t('subscription.pro.includesIntro')}
          ctaText={t('subscription.upgrade')}
          icon="✨"
        />
      </div>
    </div>
  );
}
export default SubscriptionPage;
