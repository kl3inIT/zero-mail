import type { Metadata, Route } from 'next';
import { getTranslations } from 'next-intl/server';
import { headers } from 'next/headers';
import { redirect } from 'next/navigation';

import { getCurrentUserCached } from '@/features/account/api/account-api';
import Features from '@/features/landing/components/Features';
import Hero from '@/features/landing/components/Hero';
import Testimonials from '@/features/landing/components/Testimonials';
import FAQ from '@/features/landing/components/FAQ';
import Contact from '@/features/landing/components/Contact';
import { ONBOARDING_BYPASS_ROUTE, shouldShowBetaOnboarding } from '@/features/onboarding/config';

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

export default async function LandingPage() {
  // An authenticated visitor landing on `/` is sent straight to the app instead of
  // re-seeing the marketing page (logo click, typed URL, or back-button all hit here).
  // Mirrors the CTA logic in TopBar/Hero, but as a server redirect so there is no flash.
  // Only `/` redirects — other public pages (/features, /blog, /about) stay visible
  // while signed in, so this guard lives on the page, not the public layout.
  const cookieHeader = (await headers()).get('cookie') ?? '';
  let redirectTarget: Route | null = null;
  if (cookieHeader) {
    try {
      const currentUser = await getCurrentUserCached(cookieHeader);
      redirectTarget = shouldShowBetaOnboarding(currentUser.onboardingStep)
        ? '/onboarding'
        : ONBOARDING_BYPASS_ROUTE;
    } catch {
      // Not signed in / `/me` unavailable: render the marketing page as before.
      // Never log cookies or user data here (privacy).
    }
  }
  // redirect() throws NEXT_REDIRECT — must run OUTSIDE the try/catch or it would be swallowed.
  if (redirectTarget) {
    redirect(redirectTarget);
  }

  return (
    <>
      <Hero />
      <Features />
      <Testimonials />
      <FAQ />
      <Contact />
    </>
  );
}
