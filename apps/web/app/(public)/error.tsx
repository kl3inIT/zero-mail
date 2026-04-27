'use client';

import { useEffect } from 'react';

import { useTranslations } from 'next-intl';

import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/**
 * Public segment error boundary (Phase 01.4 D-D2 + D-D3).
 * Phase 01.5 Plan 02 — deflated from PageShell/SectionCard/StatusAlert to raw
 * <main>/<Alert variant="destructive"> (D-C1, D-C2).
 *
 * Locked constraints:
 *  - 'use client' (Next.js requires error boundaries to be client components).
 *  - Receives `{ error, unstable_retry }` props (Next 16.2+ — RESEARCH §Pitfall 3).
 *  - NEVER render error.message, error.digest, or error.stack (privacy contract).
 *  - console.error only when NODE_ENV !== 'production'.
 *  - Plain DOM <button> (not <Button> wrapper) per STATE.md line 153.
 */
export default function PublicError({
  error,
  unstable_retry,
}: {
  error: Error & { digest?: string };
  unstable_retry: () => void;
}) {
  const t = useTranslations('errors.boundary');

  useEffect(() => {
    if (process.env.NODE_ENV !== 'production') {
      console.error(error);
    }
  }, [error]);

  return (
    <main className="mx-auto max-w-5xl px-4 py-12 lg:py-16">
      <Alert variant="destructive">
        <AlertTitle>{t('title')}</AlertTitle>
        <AlertDescription>{t('body')}</AlertDescription>
        <AlertAction>
          <button
            type="button"
            onClick={() => unstable_retry()}
            className={cn(buttonVariants({ variant: 'outline', size: 'sm' }))}
          >
            {t('reset')}
          </button>
        </AlertAction>
      </Alert>
    </main>
  );
}
