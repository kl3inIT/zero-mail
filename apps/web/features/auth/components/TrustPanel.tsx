import { getTranslations } from 'next-intl/server';

export async function TrustPanel() {
  const t = await getTranslations();
  const pillars = [
    { id: 'p1', titleKey: 'trust.p1.title', bodyKey: 'trust.p1.body' },
    { id: 'p2', titleKey: 'trust.p2.title', bodyKey: 'trust.p2.body' },
    { id: 'p3', titleKey: 'trust.p3.title', bodyKey: 'trust.p3.body' },
  ] as const;

  return (
    <aside
      className="hidden md:flex md:flex-col md:gap-8 md:px-12 md:py-16 lg:px-16"
      style={{ background: 'var(--trust-bg)', color: 'var(--trust-text)' }}
    >
      <div className="flex flex-col gap-2">
        <span className="text-xs font-medium tracking-wider uppercase opacity-70">
          {t('trust.eyebrow')}
        </span>
      </div>
      <ul className="flex flex-col gap-8">
        {pillars.map((pillar) => (
          <li key={pillar.id} className="flex flex-col gap-2">
            <CheckIcon className="size-5 shrink-0" />
            <h3 className="text-lg font-semibold">{t(pillar.titleKey as never)}</h3>
            <p className="text-sm leading-relaxed opacity-80">{t(pillar.bodyKey as never)}</p>
          </li>
        ))}
      </ul>
    </aside>
  );
}

function CheckIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M5 13l4 4L19 7" />
    </svg>
  );
}
