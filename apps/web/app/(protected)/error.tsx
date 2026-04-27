'use client';

import { useEffect } from 'react';

import { useTranslations } from 'next-intl';

import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/**
 * Protected segment error boundary (Phase 01.4 D-D2 + D-D3).
 * Phase 01.5 Plan 02 — deflated from PageShell/SectionCard/StatusAlert to raw
 * <main>/<Alert variant="destructive"> (D-C1, D-C2).
 *
 * Wave 0 contract test (`__tests__/app/error.test.tsx`) imports this file and
 * exercises: title/body via errors.boundary.*, no error.message/stack leak,
 * CTA calls unstable_retry, console.error gated by NODE_ENV.
 *
 * See (public)/error.tsx for locked decisions.
 */
export default function ProtectedError({
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
    <main className="mx-auto max-w-3xl space-y-6 p-6">
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
