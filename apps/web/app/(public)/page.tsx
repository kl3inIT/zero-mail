import { getTranslations } from 'next-intl/server';

import Features from '@/features/landing/components/Features';
import Hero from '@/features/landing/components/Hero';
import Pricing from '@/features/landing/components/Pricing';
import Testimonials from '@/features/landing/components/Testimonials';
import FAQ from '@/features/landing/components/FAQ';
import WaitlistDialog from '@/features/landing/components/WaitlistDialog';

export default async function LandingPage() {
  const t = await getTranslations('cta');

  return (
    <>
      <Hero />
      <Features />
      <Pricing />
      <Testimonials />
      <FAQ />
      <WaitlistDialog
        copy={{
          title: t('title.a'),
          description: t('desc'),
          emailPlaceholder: t('emailPlaceholder'),
          button: t('button'),
          closeAria: t('closeAria'),
          closeCta: t('closeCta'),
          submitting: t('submitting'),
          privacyNote: t('privacyNote'),
          successTitle: t('success.title'),
          successBody: (email: string) => t('success.body', { email }),
        }}
      />
    </>
  );
}
