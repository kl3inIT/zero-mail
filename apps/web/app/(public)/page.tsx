import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import Hero from '@/features/landing/components/Hero';
import HowItWorks from '@/features/landing/components/HowItWorks';
import Testimonials from '@/features/landing/components/Testimonials';
import FAQ from '@/features/landing/components/FAQ';
import Contact from '@/features/landing/components/Contact';

/**
 * Homepage SEO surface. Overrides the bare brand title (`common.app.title` =
 * "Zero Mail") from the root layout with a value-proposition title + a full meta
 * description, and self-references the canonical so the locale-cookie variants
 * (same URL serves vi/en, `localePrefix: 'never'`) collapse to one indexable URL.
 * Open Graph / Twitter title+description are set explicitly because Next merges
 * — it does not auto-sync the page <title> into the parent's `openGraph.title`.
 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('landing.seo');
  const title = t('title');
  const description = t('description');
  return {
    title: { absolute: title },
    description,
    alternates: { canonical: '/' },
    openGraph: { title, description },
    twitter: { title, description },
  };
}

export default function LandingPage() {
  return (
    <>
      <Hero />
      <HowItWorks />
      <Testimonials />
      <FAQ />
      <Contact />
    </>
  );
}
