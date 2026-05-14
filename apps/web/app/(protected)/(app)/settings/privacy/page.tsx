import { getTranslations } from 'next-intl/server';

import { PrivacySections } from '@/features/privacy/components/PrivacySections';

export default async function SettingsPrivacyPage() {
  const t = await getTranslations();

  return (
    <div className="mx-auto w-full max-w-4xl space-y-5 p-4 md:p-6">
      <div className="space-y-1">
        <h1 className="text-foreground text-xl font-semibold">{t('privacy.page.title')}</h1>
        <p className="text-muted-foreground max-w-3xl text-sm leading-6">
          {t('privacy.page.intro')}
        </p>
      </div>
      <PrivacySections />
    </div>
  );
}
