import { LoadingState } from '@/components/ui/LoadingState';
import { PageShell } from '@/components/ui/PageShell';

/**
 * Loading UI for the dynamic doc route (Phase 1.3 Plan 06; refactored in
 * Phase 01.4 Plan 06 onto Plan 04 LoadingState primitive — UI-SPEC §Component
 * Inventory). Renders during the RSC compile-MDX I/O so the user sees a
 * placeholder instead of a blank page.
 */
export default function DocLoading() {
  return (
    <PageShell variant="docs">
      <LoadingState variant="page" />
    </PageShell>
  );
}
