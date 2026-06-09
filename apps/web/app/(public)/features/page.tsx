import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';

import Features from '@/features/landing/components/Features';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('features');
  const title = t('seo.title');
  const description = t('seo.description');
  return {
    title: { absolute: title },
    description,
    alternates: { canonical: '/features' },
    openGraph: { title, description },
    twitter: { title, description },
  };
}

/**
 * Standalone Features page. Hosts the detailed feature deep-dives (moved off the
 * homepage to avoid duplicate content — the homepage now leads with HowItWorks),
 * behind its own canonical URL so Google can index it as a brand sitelink target.
 */
export default async function FeaturesPage() {
  const t = await getTranslations('features');
  return (
    <>
      <section className="zm-section bg-(--bg) pt-20 pb-0">
        <div className="zm-container max-w-3xl text-center">
          <h1 className="text-4xl font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('heading')}
          </h1>
          <p className="mt-5 text-xl leading-relaxed text-(--text-muted)">{t('intro')}</p>
        </div>
      </section>
      <Features />
    </>
  );
}
