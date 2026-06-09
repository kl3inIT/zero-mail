import type { Metadata } from 'next';
import Link from 'next/link';
import { getTranslations } from 'next-intl/server';

import { buttonVariants } from '@/components/ui/button';
import {
  SettingsIcon,
  ShieldIcon,
  SparklesIcon,
} from '@/features/landing/components/PrototypeIcons';
import { cn } from '@/lib/utils';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('about');
  const title = t('seo.title');
  const description = t('seo.description');
  return {
    title: { absolute: title },
    description,
    alternates: { canonical: '/about' },
    openGraph: { title, description },
    twitter: { title, description },
  };
}

/**
 * About page — the brand/story surface. Honest about the project's beta /
 * pre-launch, academic status (mirrors the privacy & terms tone). Its own
 * canonical URL makes it an indexable brand sitelink target.
 */
export default async function AboutPage() {
  const t = await getTranslations('about');

  const values = [
    { icon: <SparklesIcon size={20} />, title: t('mission.title'), body: t('mission.body') },
    { icon: <ShieldIcon size={20} />, title: t('privacy.title'), body: t('privacy.body') },
    { icon: <SettingsIcon size={20} />, title: t('status.title'), body: t('status.body') },
  ];

  return (
    <>
      {/* Hero */}
      <section className="zm-section bg-(--bg) pt-24 pb-10">
        <div className="zm-container max-w-3xl text-center">
          <h1 className="text-4xl font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('heading')}
          </h1>
          <p className="mx-auto mt-6 max-w-2xl text-xl leading-relaxed text-(--text-muted)">
            {t('lead')}
          </p>
        </div>
      </section>

      {/* Values */}
      <section className="zm-section bg-(--bg) pt-2 pb-20">
        <div className="zm-container max-w-6xl">
          <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
            {values.map((value) => (
              <div
                key={value.title}
                className="flex flex-col rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm transition-shadow hover:shadow-md"
              >
                <div className="mb-5 flex size-11 items-center justify-center rounded-xl bg-(--bg-subtle) text-(--accent)">
                  {value.icon}
                </div>
                <h2 className="mb-3 text-xl font-bold text-(--ink)">{value.title}</h2>
                <p className="text-[15px] leading-relaxed text-(--text-muted)">{value.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="zm-section bg-(--bg) pb-24">
        <div className="zm-container max-w-3xl">
          <div className="rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-10 text-center shadow-sm md:p-14">
            <h2 className="text-2xl font-bold text-(--ink) md:text-3xl">{t('cta.heading')}</h2>
            <p className="mx-auto mt-3 max-w-xl text-(--text-muted)">{t('cta.body')}</p>
            <Link
              href="/login"
              className={cn(buttonVariants({ variant: 'ink', size: 'lg' }), 'mt-7')}
            >
              {t('cta.button')}
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
