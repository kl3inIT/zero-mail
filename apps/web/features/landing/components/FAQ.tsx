import { getTranslations } from 'next-intl/server';

export default async function FAQ() {
  const t = await getTranslations('landingFaq');

  const faqs = [
    { q: t('items.q1'), a: t('items.a1') },
    { q: t('items.q2'), a: t('items.a2') },
    { q: t('items.q3'), a: t('items.a3') },
    { q: t('items.q4'), a: t('items.a4') },
    { q: t('items.q5'), a: t('items.a5') },
  ];

  // FAQPage structured data — derived from the SAME `faqs` array that renders
  // below, so the schema text always matches the visible content (a Google rich-
  // result requirement). Makes the homepage Q&A eligible for FAQ rich results and
  // gives AI search engines clean, citable question/answer passages. The cookie
  // chooses vi/en for both the rendered text and this schema, so they stay in sync.
  const faqStructuredData = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: faqs.map((faq) => ({
      '@type': 'Question',
      name: faq.q,
      acceptedAnswer: {
        '@type': 'Answer',
        text: faq.a,
      },
    })),
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(faqStructuredData) }}
      />
      <section className="zm-section bg-(--bg) py-12" id="faq">
        <div className="zm-container max-w-5xl">
          <div className="mb-16 text-center">
            <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
              {t('header.title')}
            </h2>
          </div>

          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            {faqs.map((faq) => (
              <div
                key={faq.q}
                className="rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm"
              >
                <h3 className="mb-4 text-[17px] font-bold text-(--ink)">{faq.q}</h3>
                <p className="text-[15px] leading-relaxed text-(--text-muted)">{faq.a}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
