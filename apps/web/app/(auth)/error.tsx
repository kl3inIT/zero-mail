'use client';

import { useEffect } from 'react';

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
  useEffect(() => {
    if (process.env.NODE_ENV !== 'production') {
      console.error(error);
    }
  }, [error]);

  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <div className="w-full max-w-md">
        <div className="border-destructive/30 bg-destructive/5 text-foreground rounded-lg border p-4">
          <h2 className="font-semibold">Đã xảy ra lỗi / Something went wrong</h2>
          <p className="text-muted-foreground mt-1 text-sm">Vui lòng thử lại. Please try again.</p>
          <button
            type="button"
            onClick={() => unstable_retry()}
            className="border-border bg-background hover:bg-muted mt-4 inline-flex h-8 items-center rounded-md border px-3 text-sm font-medium"
          >
            Thử lại
          </button>
        </div>
      </div>
    </main>
  );
}
