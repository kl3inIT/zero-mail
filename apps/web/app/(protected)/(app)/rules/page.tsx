import { getTranslations } from 'next-intl/server';

import { RulesWorkspace } from '@/features/rules/components/RulesWorkspace';

export default async function RulesPage() {
  const t = await getTranslations();

  return (
    <div className="mx-auto w-full max-w-6xl space-y-5 p-4 md:p-6">
      <h1 className="text-foreground text-xl font-semibold">{t('nav.rules')}</h1>
      <RulesWorkspace />
    </div>
  );
}
