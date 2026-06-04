'use client';

import { useEffect } from 'react';

/**
 * Root replacement boundary — fires when the root layout itself throws.
 *
 * Locked constraints (Phase 01.4 CONTEXT D-D2 + D-D3, RESEARCH §Pitfall 3 + 4):
 *  - Replaces the root layout, so MUST emit its own <html>/<body>.
 *  - next-intl provider is NOT mounted here — copy is English-only, inline.
 *    NEVER call useTranslations / getTranslations.
 *  - NEVER render `error.message`, `error.digest`, or `error.stack` (privacy
 *    contract D-D3 / Threat T-global-error-leak).
 *  - Recovery uses Next 16.2+ `unstable_retry`; on throw, fall back to a full
 *    page reload (Option A safety net, RESEARCH §Pitfall 3). `reset()` is NOT
 *    used — it only re-renders with stale data.
 *  - `console.error(error)` only when NODE_ENV !== 'production'.
 *  - NO error reporting SDK — observability lives in a future dedicated phase.
 *
 * Token-aware standalone surface (no provider/layout available): centered
 * stack, soft icon chip, two-weight typography, single primary CTA.
 */
export default function GlobalError({
  error,
  unstable_retry,
}: {
  error: Error & { digest?: string };
  unstable_retry: () => void;
}) {
  useEffect(() => {
    if (process.env.NODE_ENV !== 'production') {
      console.error(error);
    }
  }, [error]);

  const handleRetry = () => {
    try {
      unstable_retry();
    } catch {
      // Root replacement is broken; full reload is the only safe escape.
      window.location.reload();
    }
  };

  return (
    <html lang="en">
      <body className="bg-background text-foreground flex min-h-dvh flex-col items-center justify-center px-6 py-16 text-center">
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
        <h1 className="mt-6 text-2xl font-semibold tracking-tight sm:text-3xl">
          Something went wrong
        </h1>
        <p className="text-muted-foreground mt-3 max-w-md text-balance">
          Please try again. If the problem persists, reload the page.
        </p>
        <button
          type="button"
          onClick={handleRetry}
          className="bg-primary text-primary-foreground hover:bg-primary/90 focus-visible:ring-ring mt-8 inline-flex h-10 items-center justify-center rounded-full px-6 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
        >
          Reload
        </button>
      </body>
    </html>
  );
}
