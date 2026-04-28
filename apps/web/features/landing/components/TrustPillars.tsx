import { getTranslations } from 'next-intl/server';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  CheckIcon,
  DatabaseIcon,
  EyeIcon,
  ShieldIcon,
  UndoIcon,
} from '@/features/landing/components/PrototypeIcons';

export default async function TrustPillars() {
  const t = await getTranslations();
  const pillars = ['c1', 'c2', 'c3', 'c4'] as const;

  return (
    <>
      <section className="zm-section" id="trust">
        <div className="zm-container">
          <div className="zm-trust-band">
            <div className="zm-trust-grid">
              <div>
                <span className="zm-eyebrow text-white/60">
                  <span className="zm-dot bg-[#9DCFC8]" />
                  {t('trust.eyebrow')}
                </span>
                <h2>
                  {t('trust.title.a')}
                  <br />
                  <span className="zm-serif text-[#9DCFC8]">{t('trust.title.b')}</span>
                </h2>
                <p className="mt-5 max-w-lg text-base leading-relaxed">{t('trust.desc')}</p>
                <div className="mt-7 flex flex-wrap gap-2">
                  <span className="zm-pill zm-pill-mono border-white/15 bg-white/5 text-[#E8E7E0]">
                    {t('trust.badges.soc')}
                  </span>
                  <span className="zm-pill zm-pill-mono border-white/15 bg-white/5 text-[#E8E7E0]">
                    {t('trust.badges.casa')}
                  </span>
                  <span className="zm-pill zm-pill-mono border-white/15 bg-white/5 text-[#E8E7E0]">
                    {t('trust.badges.regions')}
                  </span>
                </div>
              </div>
              <div className="zm-trust-points">
                {pillars.map((pillar, index) => (
                  <article key={pillar} className="zm-trust-card">
                    <div className="grid grid-cols-[22px_1fr] gap-3">
                      <span className="mt-0.5 text-[#9DCFC8]">
                        {index === 0 ? (
                          <ShieldIcon size={20} />
                        ) : index === 1 ? (
                          <DatabaseIcon size={20} />
                        ) : index === 2 ? (
                          <EyeIcon size={20} />
                        ) : (
                          <UndoIcon size={20} />
                        )}
                      </span>
                      <div>
                        <h3>{t(`trust.${pillar}.title` as never)}</h3>
                        <p>{t(`trust.${pillar}.body` as never)}</p>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>
      <section className="zm-section pt-0" id="cta">
        <div className="zm-container">
          <div className="zm-final-cta">
            <div>
              <span className="zm-eyebrow">
                <span className="zm-dot" />
                {t('cta.eyebrow' as never)}
              </span>
              <h2>
                {t('cta.title.a' as never)}{' '}
                <span className="zm-serif">{t('cta.title.b' as never)}</span>
              </h2>
              <p className="mt-4 max-w-md text-base leading-relaxed text-[var(--text-muted)]">
                {t('cta.desc' as never)}
              </p>
              <form className="mt-6 flex max-w-md gap-2 rounded-[9px] border border-[var(--line-strong)] bg-[var(--bg-elevated)] p-1.5">
                <Input
                  type="email"
                  placeholder={t('cta.emailPlaceholder' as never)}
                  className="h-10 min-w-0 flex-1 border-0 bg-transparent px-3 text-sm shadow-none focus-visible:ring-0"
                />
                <Button type="submit" variant="accent" className="h-10 px-4">
                  {t('cta.button' as never)}
                </Button>
              </form>
              <div className="mt-4 flex flex-wrap gap-4 text-xs text-[var(--text-faint)]">
                {(['b1', 'b2', 'b3'] as const).map((item) => (
                  <span key={item} className="inline-flex items-center gap-1.5">
                    <CheckIcon size={12} strokeWidth={2.5} className="text-[var(--green)]" />
                    {t(`cta.${item}` as never)}
                  </span>
                ))}
              </div>
            </div>
            <div>
              <div className="grid grid-cols-2 overflow-hidden rounded-[10px] border border-[var(--line)] bg-[var(--bg-elevated)]">
                {['2,847', '50/wk', '71%', '0'].map((stat, index) => (
                  <div
                    key={stat}
                    className={`border-[var(--line)] p-6 ${index % 2 === 0 ? 'border-r' : ''} ${index < 2 ? 'border-b' : ''}`}
                  >
                    <div className="text-3xl font-semibold tracking-tight text-[var(--ink)]">
                      {stat}
                    </div>
                    <div className="mt-1 text-sm text-[var(--text-muted)]">
                      {t(`cta.stats.s${index + 1}` as never)}
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-3.5 rounded-[9px] border border-[var(--line)] bg-[var(--bg-subtle)] p-4 text-sm leading-relaxed text-[var(--text-muted)]">
                <div className="mb-1.5 flex items-center gap-2 font-medium text-[var(--ink)]">
                  <ShieldIcon size={14} className="text-[var(--accent)]" />
                  {t('cta.note.title' as never)}
                </div>
                {t('cta.note.body' as never)}
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
