import { getTranslations } from 'next-intl/server';

import { EmptyState } from '@/components/ui/EmptyState';
import { PageShell } from '@/components/ui/PageShell';

/**
 * Root 404 fallback (Phase 01.4 D-D2). RSC default async export — no client
 * directive, no interactivity beyond the EmptyState CTA link.
 *
 * Localized via `getTranslations('errors.notFound')`. UI-SPEC §Copywriting
 * Contract locks the keys: title / body / backHome.
 *
 * Composition: PageShell variant=app + EmptyState (default AlertCircle icon
 * + display title + muted body + plain anchor CTA via buttonVariants —
 * matches Plan 04 EmptyState contract).
 */
export default async function NotFound() {
  const t = await getTranslations('errors.notFound');
  return (
    <PageShell variant="app">
      <EmptyState title={t('title')} body={t('body')} cta={{ label: t('backHome'), href: '/' }} />
    </PageShell>
  );
}
