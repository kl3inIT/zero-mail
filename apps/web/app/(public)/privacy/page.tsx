import { getTranslations } from 'next-intl/server';

export default async function PrivacyPage() {
  const t = await getTranslations();

  return (
    <main className="mx-auto max-w-3xl px-4 py-8 sm:py-12">
      <h1 className="text-foreground mb-6 text-3xl font-semibold tracking-tight">
        {t('legal.privacy.placeholderTitle')}
      </h1>
      <p className="text-muted-foreground leading-relaxed">{t('legal.privacy.placeholderBody')}</p>
    </main>
  );
}
