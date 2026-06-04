'use client';

import { useEffect } from 'react';

import { useTranslations } from 'next-intl';

/**
 * Protected segment error boundary (Phase 01.4 D-D2 + D-D3).
 *
 * Wave 0 contract test (`__tests__/app/error.test.tsx`) imports this file and
 * exercises: title/body/reset via errors.boundary.*, no error.message/stack
 * leak, CTA calls unstable_retry, console.error gated by NODE_ENV. Keep those
 * three i18n keys and the retry button's accessible name intact.
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
    <main className="flex min-h-[70vh] flex-col items-center justify-center px-6 py-16 text-center">
      <div className="border-destructive/25 bg-destructive/10 text-destructive flex size-14 items-center justify-center rounded-2xl border">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={1.8}
          strokeLinecap="round"
          strokeLinejoin="round"
          className="size-7"
          aria-hidden
        >
          <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
          <line x1="12" x2="12" y1="9" y2="13" />
          <line x1="12" x2="12.01" y1="17" y2="17" />
        </svg>
      </div>
      <h1 className="text-foreground mt-6 text-2xl font-semibold tracking-tight sm:text-3xl">
        {t('title')}
      </h1>
      <p className="text-muted-foreground mt-3 max-w-md text-balance">{t('body')}</p>
      <button
        type="button"
        onClick={() => unstable_retry()}
        className="bg-primary text-primary-foreground hover:bg-primary/90 focus-visible:ring-ring mt-8 inline-flex h-10 items-center justify-center rounded-full px-6 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
      >
        {t('reset')}
      </button>
    </main>
  );
}
