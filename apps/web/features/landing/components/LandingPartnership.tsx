import { getTranslations } from 'next-intl/server';

import { CheckIcon } from '@/features/landing/components/PrototypeIcons';

/**
 * Strategic-partner spotlight for DTH Software. Static, factual marketing section (no API, no
 * quotes, no DTH credential showcase) styled with the landing token system to match the other
 * sections. The DTH logo lives at public/partners/dth-logo.svg (the brand's own asset). Per the
 * cooperation agreement, partner-logo use should carry DTH's written sign-off before going live.
 */
export async function LandingPartnership() {
  const t = await getTranslations('landingPartnership');

  const points = [t('point1'), t('point2'), t('point3')];

  return (
    <section className="bg-(--bg) py-20" id="doi-tac">
      <div className="zm-container">
        {/* Centered section header — same rhythm as the other landing sections */}
        <div className="mb-12 text-center">
          <h2 className="text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('heading')}
          </h2>
          <p className="mx-auto mt-5 max-w-2xl text-xl leading-relaxed text-(--text-muted)">
            {t('subtitle')}
          </p>
        </div>

        <div className="mx-auto max-w-5xl rounded-[28px] border border-(--line) bg-(--bg-elevated) p-8 shadow-sm md:p-12">
          <div className="grid items-center gap-10 md:grid-cols-[auto_1fr] md:gap-14">
            {/* DTH brand block — official logo asset */}
            <div className="flex flex-col items-center text-center md:items-start md:text-left">
              <div className="inline-flex items-center justify-center rounded-2xl border border-(--line) bg-(--bg) px-7 py-6">
                {/* eslint-disable-next-line @next/next/no-img-element -- Static partner SVG mark. */}
                <img
                  src="/partners/dth-logo.svg"
                  alt="DTH Software"
                  className="h-16 w-auto"
                  width={127}
                  height={64}
                />
              </div>
              <div className="mt-3 text-sm text-(--text-muted)">{t('brandLabel')}</div>
            </div>

            {/* Partnership body + what we do together */}
            <div>
              <p className="text-lg leading-relaxed text-(--text-muted)">{t('body')}</p>
              <ul className="mt-6 space-y-3">
                {points.map((point) => (
                  <li key={point} className="flex items-start gap-3">
                    <span className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-(--accent-soft) text-(--accent)">
                      <CheckIcon size={12} />
                    </span>
                    <span className="text-[15px] font-medium text-(--ink)">{point}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
