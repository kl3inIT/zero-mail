import { getTranslations } from 'next-intl/server';

function CheckIcon() {
  return (
    <svg
      className="mt-0.5 size-5 shrink-0 text-blue-500"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
    >
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
    </svg>
  );
}

export default async function Pricing() {
  const t = await getTranslations('landingPricing');

  const starterBullets = [
    t('starter.b1'),
    t('starter.b2'),
    t('starter.b3'),
    t('starter.b4'),
    t('starter.b5'),
    t('starter.b6'),
  ];
  const plusBullets = [t('plus.b1'), t('plus.b2'), t('plus.b3'), t('plus.b4'), t('plus.b5')];
  const proBullets = [t('pro.b1'), t('pro.b2'), t('pro.b3')];

  return (
    <section className="zm-section bg-(--bg)" id="pricing">
      <div className="zm-container">
        <div className="mb-16 text-center">
          <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('header.title')}
          </h2>
          <p className="mx-auto mb-10 max-w-2xl text-xl leading-relaxed text-(--text-muted)">
            {t('header.body')}
          </p>

          <p className="inline-flex items-center rounded-full border border-(--line-strong) bg-(--bg-elevated) px-5 py-2 text-sm font-semibold text-(--text-muted) shadow-sm">
            {t('header.badge')}
          </p>
        </div>

        <div className="mx-auto grid max-w-6xl grid-cols-1 gap-6 md:grid-cols-3">
          {/* Starter */}
          <div className="flex flex-col rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm transition-shadow hover:shadow-md">
            <div className="mb-6 flex items-start justify-between">
              <div className="flex size-10 items-center justify-center rounded-xl bg-(--bg-subtle) text-xl">
                💼
              </div>
            </div>
            <h3 className="mb-3 text-2xl font-bold text-(--ink)">{t('starter.title')}</h3>
            <p className="h-16 text-sm leading-relaxed text-(--text-muted)">
              {t('starter.description')}
            </p>
            <div className="mt-4 mb-8">
              <span className="text-5xl font-extrabold tracking-tight text-(--ink)">$18</span>
              <span className="text-sm text-(--text-muted)">{t('perPersonMonth')}</span>
            </div>
            <a
              href="#waitlist"
              className="mb-8 flex h-12 w-full items-center justify-center rounded-xl border border-(--line-strong) text-base font-semibold text-(--ink) hover:bg-(--bg-subtle)"
            >
              {t('ctaTrial')}
            </a>
            <div className="flex-1">
              <ul className="space-y-4 text-[15px] text-(--text-muted)">
                {starterBullets.map((text) => (
                  <li key={text} className="flex items-start gap-3">
                    <CheckIcon />
                    {text}
                  </li>
                ))}
              </ul>
            </div>
          </div>

          {/* Plus */}
          <div className="relative flex flex-col rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm transition-shadow hover:shadow-md">
            <div className="pointer-events-none absolute inset-0 rounded-[32px] border-2 border-blue-500" />
            <div className="mb-6 flex items-start justify-between">
              <div className="flex size-10 items-center justify-center rounded-xl bg-(--bg-subtle) text-xl">
                ⚡
              </div>
              <div className="flex gap-2">
                <span className="inline-flex items-center rounded-full bg-green-100 px-3 py-1 text-xs font-bold text-green-700">
                  {t('popular')}
                </span>
              </div>
            </div>
            <h3 className="mb-3 text-2xl font-bold text-(--ink)">{t('plus.title')}</h3>
            <p className="h-16 text-sm leading-relaxed text-(--text-muted)">
              {t('plus.description')}
            </p>
            <div className="mt-4 mb-8">
              <span className="text-5xl font-extrabold tracking-tight text-(--ink)">$28</span>
              <span className="text-sm text-(--text-muted)">{t('perPersonMonth')}</span>
            </div>
            <a
              href="#waitlist"
              className="mb-8 flex h-12 w-full items-center justify-center rounded-xl bg-[#3367D6] text-base font-semibold text-white shadow-md transition-all hover:bg-[#2851A8] hover:shadow-lg"
            >
              {t('ctaTrial')}
            </a>
            <div className="flex-1">
              <p className="mb-4 text-sm font-semibold text-(--ink)">{t('plus.includesIntro')}</p>
              <ul className="space-y-4 text-[15px] text-(--text-muted)">
                {plusBullets.map((text) => (
                  <li key={text} className="flex items-start gap-3">
                    <CheckIcon />
                    {text}
                  </li>
                ))}
              </ul>
            </div>
          </div>

          {/* Professional */}
          <div className="flex flex-col rounded-[32px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm transition-shadow hover:shadow-md">
            <div className="mb-6 flex items-start justify-between">
              <div className="flex size-10 items-center justify-center rounded-xl bg-(--bg-subtle) text-xl">
                ✨
              </div>
            </div>
            <h3 className="mb-3 text-2xl font-bold text-(--ink)">{t('pro.title')}</h3>
            <p className="h-16 text-sm leading-relaxed text-(--text-muted)">
              {t('pro.description')}
            </p>
            <div className="mt-4 mb-8">
              <span className="text-5xl font-extrabold tracking-tight text-(--ink)">$42</span>
              <span className="text-sm text-(--text-muted)">{t('perPersonMonth')}</span>
            </div>
            <a
              href="#waitlist"
              className="mb-8 flex h-12 w-full items-center justify-center rounded-xl border border-(--line-strong) text-base font-semibold text-(--ink) hover:bg-(--bg-subtle)"
            >
              {t('ctaTrial')}
            </a>
            <div className="flex-1">
              <p className="mb-4 text-sm font-semibold text-(--ink)">{t('pro.includesIntro')}</p>
              <ul className="space-y-4 text-[15px] text-(--text-muted)">
                {proBullets.map((text) => (
                  <li key={text} className="flex items-start gap-3">
                    <CheckIcon />
                    {text}
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
