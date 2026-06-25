import Link from 'next/link';
import { getTranslations } from 'next-intl/server';

import {
  ArrowRightIcon,
  CheckIcon,
  EyeIcon,
  ShieldIcon,
  SparklesIcon,
} from '@/features/landing/components/PrototypeIcons';

/**
 * Compact "About us" section on the landing homepage — story + Vision / Mission / Core values, a
 * marketing-forward companion to the full `/about` page (which keeps the detailed, beta-honest
 * story). Static server component on the landing token system, matching the other sections.
 */
export async function LandingAbout() {
  const t = await getTranslations('landingAbout');

  const values = [t('value1'), t('value2'), t('value3'), t('value4')];

  return (
    <section className="bg-(--bg) py-20" id="ve-chung-toi">
      <div className="zm-container">
        {/* Centered section header */}
        <div className="mb-12 text-center">
          <h2 className="text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('heading')}
          </h2>
          <p className="mx-auto mt-5 max-w-3xl text-xl leading-relaxed text-(--text-muted)">
            {t('story')}
          </p>
        </div>

        <div className="mx-auto grid max-w-5xl gap-4 md:grid-cols-3 md:gap-6">
          {/* Vision */}
          <div className="flex flex-col rounded-[24px] border border-(--line) bg-(--bg-elevated) p-8 shadow-sm">
            <div className="mb-5 flex size-11 items-center justify-center rounded-xl bg-(--accent-soft) text-(--accent)">
              <EyeIcon size={20} />
            </div>
            <h3 className="mb-3 text-xl font-bold text-(--ink)">{t('visionTitle')}</h3>
            <p className="text-[15px] leading-relaxed text-(--text-muted)">{t('visionBody')}</p>
          </div>

          {/* Mission */}
          <div className="flex flex-col rounded-[24px] border border-(--line) bg-(--bg-elevated) p-8 shadow-sm">
            <div className="mb-5 flex size-11 items-center justify-center rounded-xl bg-(--accent-soft) text-(--accent)">
              <SparklesIcon size={20} />
            </div>
            <h3 className="mb-3 text-xl font-bold text-(--ink)">{t('missionTitle')}</h3>
            <p className="text-[15px] leading-relaxed text-(--text-muted)">{t('missionBody')}</p>
          </div>

          {/* Core values */}
          <div className="flex flex-col rounded-[24px] border border-(--line) bg-(--bg-elevated) p-8 shadow-sm">
            <div className="mb-5 flex size-11 items-center justify-center rounded-xl bg-(--accent-soft) text-(--accent)">
              <ShieldIcon size={20} />
            </div>
            <h3 className="mb-3 text-xl font-bold text-(--ink)">{t('valuesTitle')}</h3>
            <ul className="space-y-2.5">
              {values.map((value) => (
                <li key={value} className="flex items-start gap-2.5">
                  <span className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-(--accent-soft) text-(--accent)">
                    <CheckIcon size={12} />
                  </span>
                  <span className="text-[15px] leading-snug text-(--text-muted)">{value}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-10 text-center">
          <Link
            href="/about"
            className="inline-flex items-center gap-1.5 text-[15px] font-semibold text-(--ink) hover:text-(--accent)"
          >
            {t('moreCta')}
            <ArrowRightIcon size={16} />
          </Link>
        </div>
      </div>
    </section>
  );
}
