import { Skeleton } from '@/components/ui/skeleton';

/**
 * Loading UI for the dynamic doc route.
 * Phase 01.5 Plan 02 — deflated from PageShell/LoadingState to raw <main>/Skeleton
 * composition (D-C1, D-C2). Mirrors the LoadingState 'page' variant skeleton layout.
 */
export default function DocLoading() {
  return (
    <main className="mx-auto max-w-3xl space-y-6 px-4 py-8">
      <Skeleton className="h-10 w-2/3" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-64 w-full" />
    </main>
  );
}
