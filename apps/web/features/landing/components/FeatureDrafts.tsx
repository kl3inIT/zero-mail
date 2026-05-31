import Image from 'next/image';
import { getTranslations } from 'next-intl/server';

export async function FeatureDrafts() {
  const t = await getTranslations('landingFeatures');

  return (
    <section className="zm-container text-center">
      <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
        {t('sec2.title')}
      </h2>
      <p className="mx-auto mb-16 max-w-3xl text-xl leading-relaxed text-(--text-muted)">
        {t('sec2.body')}
      </p>
      <div className="relative mx-auto aspect-[16/9] w-full max-w-5xl overflow-hidden rounded-2xl">
        {/* Light Mode Image */}
        <Image
          src="/images/ketnoi-light.png"
          alt={t('sec2.title')}
          fill
          sizes="(max-width: 1024px) 100vw, 1024px"
          className="object-contain dark:hidden"
        />
        {/* Dark Mode Image */}
        <Image
          src="/images/ketnoi-dark.png"
          alt={t('sec2.title')}
          fill
          sizes="(max-width: 1024px) 100vw, 1024px"
          className="hidden object-contain dark:block"
        />
      </div>
    </section>
  );
}
