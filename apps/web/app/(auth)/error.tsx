'use client';

import { useEffect } from 'react';

import { PageShell } from '@/components/ui/PageShell';
import { SectionCard } from '@/components/ui/SectionCard';
import { StatusAlert } from '@/components/ui/StatusAlert';

/**
 * Auth segment error boundary (Phase 01.4 D-D2 + D-D3).
 *
 * Identical contract to (public)/error.tsx and (protected)/error.tsx —
 * differs only in PageShell variant (`auth` for the centered max-w-md
 * shell that matches /login). See (public)/error.tsx for the locked
 * decisions (unstable_retry over reset, privacy contract on error.*,
 * env-gated console.error, no SDK).
 *
 * SectionCard heading omitted — StatusAlert owns the title rendering.
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
    <PageShell variant="auth">
      <SectionCard>
        <StatusAlert
          variant="error"
          titleKey="errors.boundary.title"
          bodyKey="errors.boundary.body"
          cta={{ labelKey: 'errors.boundary.reset', onClick: () => unstable_retry() }}
        />
      </SectionCard>
    </PageShell>
  );
}
