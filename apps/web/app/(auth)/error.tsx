'use client';

import { useEffect } from 'react';

import { useTranslations } from 'next-intl';

import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/**
 * Auth segment error boundary (Phase 01.4 D-D2 + D-D3).
 * Phase 01.5 Plan 02 — deflated from PageShell/SectionCard/StatusAlert to raw
 * <main>/<Alert variant="destructive"> (D-C1, D-C2).
 *
 * Uses max-w-md (matching the login surface) with flex-centering.
 * See (public)/error.tsx for locked decisions.
 */
export default function AuthError({
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
    <main className="flex min-h-screen items-center justify-center p-6">
      <div className="w-full max-w-md">
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
      </div>
    </main>
  );
}
