import Image from 'next/image';
import { getTranslations } from 'next-intl/server';

export async function FeatureOrganized() {
  const t = await getTranslations('landingFeatures');

  return (
    <section className="zm-container text-center">
      <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
        {t('sec1.title.line1')}
        <br />
        {t('sec1.title.line2')}
      </h2>
      <p className="mx-auto mb-16 max-w-3xl text-xl leading-relaxed text-(--text-muted)">
        {t('sec1.body')}
      </p>

      <div className="mx-auto mb-8 grid w-full max-w-5xl grid-cols-2">
        <div className="flex items-center justify-center gap-2 text-center">
          <span className="text-2xl font-bold text-(--text-muted)">{t('sec1.before')}</span>
          <span className="relative -top-2 rounded-full bg-red-700 px-2 py-0.5 text-[11px] leading-none font-extrabold text-white shadow-sm">
            99+
          </span>
        </div>
        <div className="text-center">
          <span className="text-2xl font-bold text-(--text-muted)">{t('sec1.after')}</span>
        </div>
      </div>

      <div className="relative mx-auto aspect-[16/9] w-full max-w-5xl overflow-hidden rounded-2xl">
        {/* Light Mode Image */}
        <Image
          src="/images/phan-loai-dark-v2.png"
          alt={t('sec1.title.line1')}
          fill
          sizes="(max-width: 1024px) 100vw, 1024px"
          className="object-contain dark:hidden"
        />
        {/* Dark Mode Image */}
        <Image
          src="/images/phan-loai-light.png"
          alt={t('sec1.title.line1')}
          fill
          sizes="(max-width: 1024px) 100vw, 1024px"
          className="hidden object-contain dark:block"
        />
      </div>
    </section>
  );
}
