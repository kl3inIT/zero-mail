import { getTranslations } from 'next-intl/server';

import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/**
 * Root 404 fallback (Phase 01.4 D-D2). RSC default async export.
 *
 * Inline content only — NOT lucide-react / next/link (STATE.md line 149: the
 * vitest React-dedupe boundary won't survive those imports). The big "404"
 * glyph is decorative (aria-hidden); the heading carries the accessible name.rob
 */
export default async function NotFound() {
  const t = await getTranslations('errors.notFound');
  return (
    <main className="flex min-h-[80vh] flex-col items-center justify-center px-6 py-16 text-center">
      <p
        aria-hidden
        className="text-muted-foreground/20 text-[7rem] leading-none font-bold tracking-tighter select-none sm:text-[9rem]"
      >
        404
      </p>
      <h1 className="text-foreground mt-2 text-2xl font-semibold tracking-tight sm:text-3xl">
        {t('title')}
      </h1>
      <p className="text-muted-foreground mt-3 max-w-md text-balance">{t('body')}</p>
      {/* eslint-disable-next-line @next/next/no-html-link-for-pages -- vitest React-dedupe boundary cannot import next/link (STATE.md line 149) */}
      <a href="/" className={cn(buttonVariants(), 'mt-8 h-11 rounded-full px-7')}>
        {t('backHome')}
      </a>
    </main>
  );
}
