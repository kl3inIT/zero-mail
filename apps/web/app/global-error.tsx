'use client';

import { useEffect } from 'react';

/**
 * Root replacement boundary — fires when the root layout itself throws.
 *
 * Locked constraints (Phase 01.4 CONTEXT D-D2 + D-D3, RESEARCH §Pitfall 3 +
 * §Pitfall 4, Plan 05 Task 1 File 1):
 *  - Replaces the root layout, so MUST emit its own <html>/<body>.
 *  - next-intl provider is NOT mounted at this point — copy is English-only,
 *    inline, and minimal. NEVER call useTranslations / getTranslations here.
 *  - NEVER render `error.message`, `error.digest`, or `error.stack` (privacy
 *    contract D-D3 / Threat T-global-error-leak: server-thrown ApiError or
 *    LLM-derived exception text could carry tenant fragments).
 *  - Recovery affordance uses Next 16.2+ `unstable_retry` (re-fetches +
 *    re-renders). `reset()` is NOT used — it only re-renders with stale data
 *    (RESEARCH §Pitfall 3). On `unstable_retry()` throwing (the underlying
 *    fetcher context may also be broken), fall back to a full page reload —
 *    the only safe escape from a broken root replacement (Option A safety
 *    net per RESEARCH §Pitfall 3 + checker Issue 2).
 *  - `console.error(error)` only when NODE_ENV !== 'production' (browser
 *    DevTools convenience for the developer; never in shipped builds).
 *  - NO error reporting SDK (Sentry/Datadog/OTel) — observability lives in
 *    a future dedicated phase.
 *
 * Refined-minimalist visual: centered card-less stack, two-weight typography
 * (semibold display + regular body), single primary CTA, token-aware
 * background/foreground/primary colors. No decorative ornament.
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
      // Browser DevTools only — gated by env to avoid leaking PII into a
      // production log path (Threat T-global-error-leak).
      console.error(error);
    }
  }, [error]);

  const handleRetry = () => {
    try {
      unstable_retry();
    } catch {
      // Root replacement is broken; full reload is the only safe escape
      // (Option A fallback, RESEARCH §Pitfall 3). Never fall back to
      // `reset()` — that would perpetuate stale data.
      window.location.reload();
    }
  };

  return (
    <html lang="en">
      <body className="bg-background text-foreground flex min-h-dvh items-center justify-center p-6">
        <div className="max-w-md space-y-4 text-center">
          <h1 className="text-3xl font-semibold tracking-tight">Something went wrong</h1>
          <p className="text-muted-foreground">
            Please try again. If the problem persists, reload the page.
          </p>
          <button
            type="button"
            onClick={handleRetry}
            className="bg-primary text-primary-foreground hover:bg-primary/90 focus-visible:ring-ring inline-flex h-10 items-center justify-center rounded-md px-4 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
          >
            Reload
          </button>
        </div>
      </body>
    </html>
  );
}
