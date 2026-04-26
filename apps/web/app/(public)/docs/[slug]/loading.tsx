import { getTranslations } from 'next-intl/server';

/**
 * Loading UI for the dynamic doc route (Phase 1.3 Plan 06).
 *
 * Renders during the RSC compile-MDX I/O so the user sees a placeholder
 * instead of a blank page. Uses the existing `common.loading` key (Phase 1.1).
 */
export default async function DocLoading() {
  const t = await getTranslations('common');
  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <p className="text-muted-foreground text-sm">{t('loading')}</p>
    </div>
  );
}
