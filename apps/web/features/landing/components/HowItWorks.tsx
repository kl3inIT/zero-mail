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

  return (
    <section className="zm-section zm-how-section" id="how">
      <div className="zm-container">
        <div className="zm-section-head">
          <span className="zm-eyebrow">
            <span className="zm-dot" />
            {t('how.eyebrow')}
          </span>
          <h2>{t('how.title')}</h2>
          <p>{t('how.desc')}</p>
        </div>

        <div className="zm-how-grid">
          {/* Step 01 */}
          <article className="zm-how-card">
            <div className="zm-how-num" aria-hidden="true">
              01
            </div>
            <div className="zm-how-icon">
              <MailIcon size={18} />
            </div>
            <h3>{t('how.step1.title')}</h3>
            <p>{t('how.step1.desc')}</p>
            <div className="zm-how-visual">
              <div className="flex items-center gap-3 rounded-xl border border-[var(--line)] bg-[var(--bg)] p-4">
                <span className="zm-brand-mark">
                  <ZMLogoMark size={14} />
                </span>
                <span className="h-px flex-1 bg-[repeating-linear-gradient(90deg,var(--accent),var(--accent)_4px,transparent_4px,transparent_8px)]" />
                <span className="grid size-8 place-items-center rounded-lg border border-[var(--line)] bg-white text-xs font-semibold text-[#555]">
                  M
                </span>
              </div>
              <div className="mt-3 flex items-center gap-2 font-mono text-[12px] text-[var(--green)]">
                <CheckIcon size={13} strokeWidth={3} />
                <span>{t('how.step1.connected')}</span>
              </div>
              <div className="mt-1.5 flex items-center gap-1.5 font-mono text-[11px] text-[var(--text-faint)]">
                <ShieldIcon size={11} />
                <span>{t('how.step1.scope')}</span>
              </div>
            </div>
          </article>

          {/* Connector */}
          <div className="zm-how-connector" aria-hidden="true">
            <div className="zm-how-connector-line" />
            <div className="zm-how-connector-dot" />
          </div>

          {/* Step 02 */}
          <article className="zm-how-card">
            <div className="zm-how-num" aria-hidden="true">
              02
            </div>
            <div className="zm-how-icon">
              <PenIcon size={18} />
            </div>
            <h3>{t('how.step2.title')}</h3>
            <p>{t('how.step2.desc')}</p>
            <div className="zm-how-visual">
              <div className="flex items-center gap-2 rounded-xl border border-[var(--line)] bg-[var(--bg)] p-3 font-mono text-[12.5px] text-[var(--text)]">
                <SparklesIcon size={13} className="shrink-0 text-[var(--accent)]" />
                <span className="min-w-0 truncate">{t('how.step2.example')}</span>
                <span className="h-4 w-px shrink-0 animate-pulse bg-[var(--ink)]" />
              </div>
              <div className="mt-3 flex flex-wrap gap-1.5">
                <span className="zm-pill zm-pill-mono pill-accent">{t('action.archive')}</span>
                <span className="zm-pill zm-pill-mono">{t('feat.tester.r2')}</span>
                <span className="zm-pill zm-pill-mono">{t('feat.tester.r1')}</span>
                <span className="zm-pill zm-pill-mono">amount &lt; $500</span>
              </div>
              <div className="mt-3 flex items-center gap-1.5 font-mono text-[11.5px] text-[var(--green)]">
                <CheckIcon size={12} strokeWidth={2.5} />
                {t('how.step2.parsed')}
              </div>
            </div>
          </article>

          {/* Connector */}
          <div className="zm-how-connector" aria-hidden="true">
            <div className="zm-how-connector-line" />
            <div className="zm-how-connector-dot" />
          </div>

          {/* Step 03 */}
          <article className="zm-how-card">
            <div className="zm-how-num" aria-hidden="true">
              03
            </div>
            <div className="zm-how-icon">
              <ListIcon size={18} />
            </div>
            <h3>{t('how.step3.title')}</h3>
            <p>{t('how.step3.desc')}</p>
            <div className="zm-how-visual">
              <div className="zm-audit max-h-none rounded-xl p-3 text-[11.5px]">
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
            </div>
          </article>
        </div>
      </div>
    </section>
  );
}
