import { dehydrate, HydrationBoundary, QueryClient } from '@tanstack/react-query';
import { cookies, headers } from 'next/headers';
import { redirect } from 'next/navigation';
import { Suspense } from 'react';

import { fetchAnalyticsSummary } from '@/features/analytics/api/analytics-api';
import { AnalyticsPageClient } from '@/features/analytics/components/AnalyticsPageClient';
import { AnalyticsSkeleton } from '@/features/analytics/components/AnalyticsSkeleton';
import { normalizeAnalyticsWindow } from '@/features/analytics/components/analytics-window';
import { analyticsKeys } from '@/features/analytics/query-keys';

export default async function AnalyticsPage({
  searchParams,
}: {
  searchParams: Promise<{ window?: string }>;
}) {
  const { window: rawWindow } = await searchParams;
  const selectedWindow = normalizeAnalyticsWindow(rawWindow ?? null);

  // Canonicalize the window query param server-side so the client never has to
  // redirect inside an effect (which would flash the wrong window then replace).
  if (rawWindow !== selectedWindow) {
    redirect(`/analytics?window=${selectedWindow}`);
  }

  // Forward session cookie + Playwright stub headers so the server-side
  // prefetch hits the backend authenticated. Without this the SSR pre-render
  // of the client component gets 401 â†’ "Switched to client rendering" warning.
  const [cookieStore, incomingHeaders] = await Promise.all([cookies(), headers()]);
  const requestHeaders = backendRequestHeaders(cookieStore.toString(), incomingHeaders);

  const queryClient = new QueryClient();
  await queryClient.prefetchQuery({
    queryKey: analyticsKeys.summary(selectedWindow),
    queryFn: () => fetchAnalyticsSummary(selectedWindow, { headers: requestHeaders }),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <div className="flex h-full flex-col">
        <div className="flex-1 space-y-4 overflow-auto p-3 sm:p-4">
          <Suspense fallback={<AnalyticsSkeleton />}>
            <AnalyticsPageClient />
          </Suspense>
        </div>
      </div>
    </HydrationBoundary>
  );
}

function backendRequestHeaders(
  cookieHeader: string,
  incomingHeaders: { get(name: string): string | null },
): HeadersInit | undefined {
  const requestHeaders: Record<string, string> = {};
  if (cookieHeader) requestHeaders.Cookie = cookieHeader;

  const testSubject = incomingHeaders.get('x-test-subject');
  const testEmail = incomingHeaders.get('x-test-email');
  if (testSubject) requestHeaders['X-Test-Subject'] = testSubject;
  if (testEmail) requestHeaders['X-Test-Email'] = testEmail;

  return Object.keys(requestHeaders).length === 0 ? undefined : requestHeaders;
}
