import { getTranslations } from 'next-intl/server';

import { RulesWorkspace } from '@/features/rules/components/RulesWorkspace';

export default async function RulesPage() {
  const t = await getTranslations();

  return (
    <div className="mx-auto w-full max-w-6xl space-y-5 p-4 md:p-6">
      <div className="space-y-1">
        <h1 className="text-foreground text-xl font-semibold">{t('rules.page.title')}</h1>
        <p className="text-muted-foreground max-w-3xl text-sm leading-6">{t('rules.page.intro')}</p>
      </div>
      <RulesWorkspace />
    </div>
  );
}
