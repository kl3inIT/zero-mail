import { getTranslations } from 'next-intl/server';

/**
 * Public Terms of Service page (quick task 260526-qal).
 *
 * Server component — uses next-intl `getTranslations()` and iterates a stable
 * ordered tuple of section ids so the TOC and the body stay in lockstep.
 * The (public) route group layout owns <main>/header/footer chrome; this
 * file renders exactly one <h1> and one <section> wrapper.
 *
 * Section bodies are stored in i18n as plain text with `\n\n` paragraph
 * delimiters; `whitespace-pre-line` collapses single newlines but renders
 * paragraph breaks, avoiding a per-paragraph i18n key explosion.
 *
 * The `as never` cast on dynamic template-literal `t(...)` keys is the
 * established repo pattern for the next-intl 4 typed-namespace bypass on
 * dynamic keys (Phase 1.3 Plan 05 precedent — see STATE.md decisions).
 */

const TERMS_SECTION_IDS = [
  'acceptance',
  'description',
  'eligibility',
  'gmailAuthorization',
  'autoSendRules',
  'creditsAndBilling',
  'refunds',
  'acceptableUse',
  'intellectualProperty',
  'warrantiesDisclaimer',
  'liability',
  'termination',
  'changes',
  'governingLaw',
  'contact',
] as const;

export default async function TermsPage() {
  const t = await getTranslations();

  return (
    <section className="mx-auto max-w-3xl px-4 py-8 sm:py-12">
      <header className="border-border mb-8 border-b pb-6">
        <h1 className="text-foreground mb-3 text-3xl font-semibold tracking-tight">
          {t('legal.terms.title')}
        </h1>
        <p className="text-muted-foreground text-sm">{t('legal.lastUpdated')}</p>
        <p className="text-foreground mt-4 leading-relaxed">{t('legal.terms.intro')}</p>
      </header>

      <nav
        aria-label={t('legal.tocHeading')}
        className="border-border bg-card mb-10 rounded-md border p-5"
      >
        <h2 className="text-foreground mb-3 text-sm font-semibold tracking-wide uppercase">
          {t('legal.tocHeading')}
        </h2>
        <ol className="text-muted-foreground list-decimal space-y-1.5 pl-5 text-sm">
          {TERMS_SECTION_IDS.map((id) => (
            <li key={id}>
              <a
                href={`#${id}`}
                className="hover:text-foreground underline-offset-4 hover:underline"
              >
                {t(`legal.terms.toc.${id}` as never)}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <div className="space-y-10">
        {TERMS_SECTION_IDS.map((id) => (
          <article key={id} id={id} className="scroll-mt-20">
            <h2 className="text-foreground mb-3 text-xl font-semibold tracking-tight">
              {t(`legal.terms.sections.${id}.heading` as never)}
            </h2>
            <div className="text-foreground/90 leading-relaxed whitespace-pre-line">
              {t(`legal.terms.sections.${id}.body` as never)}
            </div>
          </article>
        ))}
      </div>

      <footer className="border-border text-muted-foreground mt-12 border-t pt-6 text-sm">
        <p>{t('legal.contact.body')}</p>
      </footer>
    </section>
  );
}
