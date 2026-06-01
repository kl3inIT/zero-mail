import Image from 'next/image';
import { getTranslations } from 'next-intl/server';

export async function FeatureBulkUnsubscribe() {
  const t = await getTranslations('landingFeatures');

  return (
    <section className="zm-container mb-24 text-center">
      <h2 className="mb-6 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
        {t('sec5.title')}
      </h2>
      <p className="mx-auto mb-16 max-w-3xl text-xl leading-relaxed text-(--text-muted)">
        {t('sec5.body')}
      </p>
      <div className="relative mx-auto aspect-[16/9] w-full max-w-4xl overflow-hidden rounded-2xl border border-(--line-strong) bg-(--bg-elevated) shadow-[0_8px_30px_rgba(0,0,0,0.06)] md:aspect-[2/1]">
        <Image
          src="/images/huydangky-light.png"
          alt={t('sec5.title')}
          fill
          sizes="(min-width: 1024px) 56rem, 100vw"
          className="object-contain p-4 md:p-8 dark:hidden"
        />
        <Image
          src="/images/huydangky-dark.png"
          alt={t('sec5.title')}
          fill
          sizes="(min-width: 1024px) 56rem, 100vw"
          className="hidden object-contain p-4 md:p-8 dark:block"
        />
      </div>
    </section>
  );
}
