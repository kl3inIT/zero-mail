'use client';

import { useEffect } from 'react';

import { PageShell } from '@/components/ui/PageShell';
import { SectionCard } from '@/components/ui/SectionCard';
import { StatusAlert } from '@/components/ui/StatusAlert';

/**
 * Protected segment error boundary (Phase 01.4 D-D2 + D-D3).
 *
 * Identical contract to (public)/error.tsx and (auth)/error.tsx — differs
 * only in PageShell variant (`app` for the standard max-w-3xl protected
 * shell). See (public)/error.tsx for the locked decisions.
 *
 * Wave 0 contract test (`__tests__/app/error.test.tsx`) imports this file
 * specifically (`@/app/(protected)/error`) and exercises:
 *  - title/body resolved via errors.boundary.{title,body}
 *  - error.message / error.stack NOT rendered (privacy)
 *  - clicking the CTA calls `unstable_retry`
 *  - console.error gated by NODE_ENV
 *
 * SectionCard heading omitted — StatusAlert renders the title once and
 * the test asserts a single match for it.
 */
export default function ProtectedError({
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
    <PageShell variant="app">
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
