import { getTranslations } from 'next-intl/server';

import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

/**
 * Root 404 fallback (Phase 01.4 D-D2). RSC default async export.
 * Phase 01.5 Plan 02 — deflated from PageShell/EmptyState to raw
 * <main>/<Card> composition (D-C1, D-C2).
 *
 * Inline SVG (AlertCircle) — NOT lucide-react (STATE.md line 149:
 * vitest React-dedupe boundary won't survive lucide imports).
 */
export default async function NotFound() {
  const t = await getTranslations('errors.notFound');
  return (
    <main className="mx-auto max-w-3xl space-y-6 p-6">
      <Card className="border-dashed">
        <CardContent className="flex flex-col items-center gap-4 py-10 text-center">
          <div aria-hidden className="text-muted-foreground">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              className="size-12"
            >
              <circle cx="12" cy="12" r="10" />
              <line x1="12" x2="12" y1="8" y2="12" />
              <line x1="12" x2="12.01" y1="16" y2="16" />
            </svg>
          </div>
          <div className="space-y-2">
            <h2 className="text-foreground text-3xl font-semibold tracking-tight">{t('title')}</h2>
            <p className="text-muted-foreground">{t('body')}</p>
          </div>
          {/* eslint-disable-next-line @next/next/no-html-link-for-pages -- vitest React-dedupe boundary cannot import next/link (STATE.md line 149) */}
          <a href="/" className={buttonVariants()}>
            {t('backHome')}
          </a>
        </CardContent>
      </Card>
    </main>
  );
}
