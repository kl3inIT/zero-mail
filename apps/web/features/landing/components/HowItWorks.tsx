import { getTranslations } from 'next-intl/server';

import ZMLogoMark from '@/features/landing/components/ZMLogoMark';
import {
  CheckIcon,
  ListIcon,
  MailIcon,
  PenIcon,
  ShieldIcon,
  SparklesIcon,
} from '@/features/landing/components/PrototypeIcons';

export default async function HowItWorks() {
  const t = await getTranslations();
  const steps = [
    { key: 'step1', titleKey: 'how.step1.title', descKey: 'how.step1.desc' },
    { key: 'step2', titleKey: 'how.step2.title', descKey: 'how.step2.desc' },
    { key: 'step3', titleKey: 'how.step3.title', descKey: 'how.step3.desc' },
  ] as const;

  return (
    <section className="zm-section" id="how">
      <div className="zm-container">
        <div className="zm-section-head">
          <span className="zm-eyebrow">
            <span className="zm-dot" />
            {t('how.eyebrow')}
          </span>
          <h2>{t('how.title')}</h2>
          <p>{t('how.desc')}</p>
        </div>
        <div className="zm-steps">
          {steps.map((step, index) => (
            <article key={step.key} className="zm-step">
              <div className="zm-step-head">
                <span className="zm-step-icon" aria-hidden="true">
                  {index === 0 ? (
                    <MailIcon size={16} />
                  ) : index === 1 ? (
                    <PenIcon size={16} />
                  ) : (
                    <ListIcon size={16} />
                  )}
                </span>
                <span className="zm-step-num">0{index + 1} / 03</span>
              </div>
              <h3>{t(step.titleKey as never)}</h3>
              <p>{t(step.descKey as never)}</p>
              <div className="zm-step-visual">
                {index === 0 && (
                  <div className="grid gap-3">
                    <div className="flex items-center gap-3 rounded-lg border border-[var(--line)] bg-[var(--bg-elevated)] p-3">
                      <span className="zm-brand-mark">
                        <ZMLogoMark size={14} />
                      </span>
                      <span className="h-px flex-1 bg-[repeating-linear-gradient(90deg,var(--accent),var(--accent)_4px,transparent_4px,transparent_8px)]" />
                      <span className="grid size-7 place-items-center rounded-md border border-[var(--line)] bg-white text-xs">
                        M
                      </span>
                      <span className="inline-flex items-center gap-1 font-mono text-[11px] text-[var(--green)]">
                        <CheckIcon size={11} strokeWidth={3} /> {t('how.step1.connected')}
                      </span>
                    </div>
                    <div className="flex gap-1.5 font-mono text-[11.5px] text-[var(--text-faint)]">
                      <ShieldIcon size={12} />
                      <span>{t('how.step1.scope')}</span>
                    </div>
                  </div>
                )}
                {index === 1 && (
                  <div className="grid gap-2">
                    <div className="flex items-center gap-2 rounded-lg border border-[var(--line)] bg-[var(--bg-elevated)] p-3 font-mono text-xs text-[var(--text)]">
                      <SparklesIcon size={12} className="text-[var(--accent)]" />
                      {t('how.step2.example')}
                      <span className="ml-auto h-4 w-px animate-pulse bg-[var(--ink)]" />
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      <span className="zm-pill zm-pill-mono">{t('action.archive')}</span>
                      <span className="zm-pill zm-pill-mono">{t('feat.tester.r2')}</span>
                      <span className="zm-pill zm-pill-mono">{t('feat.tester.r1')}</span>
                      <span className="zm-pill zm-pill-mono">amount &lt; $500</span>
                    </div>
                    <div className="flex items-center gap-1.5 font-mono text-[11.5px] text-[var(--text-faint)]">
                      <CheckIcon size={12} strokeWidth={2.5} className="text-[var(--green)]" />
                      {t('how.step2.parsed')}
                    </div>
                  </div>
                )}
                {index === 2 && (
                  <div className="zm-audit max-h-none p-3 text-[11px]">
                    <div className="zm-audit-row">
                      <span className="zm-audit-time">9:14a</span>
                      <span>·</span>
                      <span>
                        <span className="zm-audit-action">{t('how.audit.archived')}</span>{' '}
                        {t('how.audit.r1')}
                      </span>
                    </div>
                    <div className="zm-audit-row">
                      <span className="zm-audit-time">9:14a</span>
                      <span>·</span>
                      <span>
                        <span className="zm-audit-action text-[var(--accent)]">
                          {t('how.audit.labeled')}
                        </span>{' '}
                        {t('how.audit.r2')}
                      </span>
                    </div>
                    <div className="zm-audit-row">
                      <span className="zm-audit-time">9:13a</span>
                      <span>·</span>
                      <span>
                        <span className="zm-audit-action text-[var(--blue)]">
                          {t('how.audit.drafted')}
                        </span>{' '}
                        {t('how.audit.r3')}
                      </span>
                    </div>
                    <div className="zm-audit-row">
                      <span className="zm-audit-time">9:13a</span>
                      <span>·</span>
                      <span>
                        <span className="zm-audit-action text-[var(--text-faint)]">
                          {t('how.audit.skipped')}
                        </span>{' '}
                        {t('how.audit.r4')}
                      </span>
                    </div>
                  </div>
                )}
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
