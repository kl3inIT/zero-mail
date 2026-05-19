import { getTranslations } from 'next-intl/server';

export default async function FAQ() {
  const t = await getTranslations('landing.faq');

  const items = [
    { q: t('items.i1.q'), a: t('items.i1.a') },
    { q: t('items.i2.q'), a: t('items.i2.a') },
    { q: t('items.i3.q'), a: t('items.i3.a') },
    { q: t('items.i4.q'), a: t('items.i4.a') },
    { q: t('items.i5.q'), a: t('items.i5.a') },
  ];

  return (
    <section className="zm-section bg-(--bg) py-24" id="faq">
      <div className="zm-container max-w-5xl">
        <div className="mb-16 text-center">
          <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('title')}
          </h2>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          {items.map((item, index) => (
            <div
              key={index}
              className="rounded-[24px] border border-(--line-strong) bg-(--bg-elevated) p-8 shadow-sm"
            >
              <h4 className="mb-4 text-[17px] font-bold text-(--ink)">{item.q}</h4>
              <p className="text-[15px] leading-relaxed text-(--text-muted)">{item.a}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
