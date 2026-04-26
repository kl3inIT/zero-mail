'use client';

import { useEffect } from 'react';

import { PageShell } from '@/components/ui/PageShell';
import { SectionCard } from '@/components/ui/SectionCard';
import { StatusAlert } from '@/components/ui/StatusAlert';

/**
 * Public segment error boundary (Phase 01.4 D-D2 + D-D3).
 *
 * Locked constraints:
 *  - 'use client' (Next.js requires error boundaries to be client components).
 *  - Receives `{ error, unstable_retry }` props (Next 16.2+ — RESEARCH
 *    §Pitfall 3 + checker Issue 2). `reset` is NOT used (re-renders stale
 *    state); `unstable_retry()` re-fetches.
 *  - All copy via next-intl `errors.boundary.*` keys (D-D3 + UI-SPEC).
 *  - NEVER render `error.message`, `error.digest`, or `error.stack` (privacy
 *    contract / Threat T-error-leak).
 *  - `console.error(error)` only when NODE_ENV !== 'production'.
 *  - NO error reporting SDK.
 *
 * Composition: PageShell variant=marketing (matches the public surface
 * width contract) + SectionCard + StatusAlert variant=error with the CTA
 * wired to `unstable_retry`. StatusAlert's CTA renders as a plain DOM
 * button (Plan 04 deviation: `Button` wrapper from @base-ui/react fails
 * under vitest's React-dedupe boundary).
 */
export default function PublicError({
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

  // SectionCard heading intentionally OMITTED — StatusAlert already renders
  // `errors.boundary.title` once. Duplicating it would surface the same
  // string twice in the DOM, breaking the single-match `getByText` contract
  // and producing visual redundancy. StatusAlert owns its own next-intl
  // lookup; the boundary itself does not need a `useTranslations()` hook.

  return (
    <PageShell variant="marketing">
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
