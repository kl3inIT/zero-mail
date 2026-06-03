'use client';

import { useEffect } from 'react';

import Link from 'next/link';

/**
 * Auth segment error boundary (Phase 01.4 D-D2 + D-D3).
 *
 * Uses min-h-screen flex-centering to match the login surface.
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
    <main className="flex min-h-screen flex-col items-center justify-center px-6 py-16 text-center">
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
        Đã xảy ra lỗi
      </h1>
      <p className="text-muted-foreground mt-3 max-w-md text-balance">
        Không thể tải trang đăng nhập. Vui lòng thử lại.
      </p>
      <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
        <button
          type="button"
          onClick={() => unstable_retry()}
          className="bg-primary text-primary-foreground hover:bg-primary/90 focus-visible:ring-ring inline-flex h-10 items-center justify-center rounded-full px-6 text-sm font-medium focus-visible:ring-2 focus-visible:outline-none"
        >
          Thử lại
        </button>
        <Link
          href="/login"
          className="border-border bg-background hover:bg-muted text-foreground inline-flex h-10 items-center justify-center rounded-full border px-6 text-sm font-medium"
        >
          Về đăng nhập
        </Link>
      </div>
    </main>
  );
}
