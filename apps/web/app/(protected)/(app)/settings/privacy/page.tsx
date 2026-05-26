import { getTranslations } from 'next-intl/server';

import { PrivacySections } from '@/features/privacy/components/PrivacySections';

export default async function SettingsPrivacyPage() {
  const t = await getTranslations();

  return (
    <div className="flex h-full flex-col">
      <div className="border-border border-b px-4 py-3">
        <h1 className="text-foreground text-[17px] font-semibold">{t('privacy.page.title')}</h1>
        <p className="text-muted-foreground text-sm">{t('privacy.page.intro')}</p>
      </div>
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <PrivacySections />
      </div>
    </div>
  );
}
