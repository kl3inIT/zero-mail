import { dehydrate, HydrationBoundary, QueryClient } from '@tanstack/react-query';
import { cookies, headers } from 'next/headers';

import { AppShell } from '@/components/shell/AppShell';
import { getCurrentUser } from '@/features/account/api/account-api';
import { accountQueryKeys } from '@/features/account/query-keys';
import { getBillingBalance, getBillingPlans } from '@/features/billing/api/billing-api';
import { billingKeys } from '@/features/billing/query-keys';
import { getTenantStatus } from '@/features/gmail/api/gmail-api';
import { gmailQueryKeys } from '@/features/gmail/query-keys';

const SIDEBAR_COOKIE_NAME = 'sidebar_state';

export default async function ProtectedAppLayout({ children }: { children: React.ReactNode }) {
  const cookieStore = await cookies();
  const incomingHeaders = await headers();
  const cookieHeader = cookieStore.toString();
  const requestHeaders = backendRequestHeaders(cookieHeader, incomingHeaders);
  const defaultSidebarOpen = cookieStore.get(SIDEBAR_COOKIE_NAME)?.value !== 'false';
  const queryClient = new QueryClient();

  await Promise.all([
    queryClient.prefetchQuery({
      queryKey: accountQueryKeys.me(),
      queryFn: () => getCurrentUser({ headers: requestHeaders }),
    }),
    queryClient.prefetchQuery({
      queryKey: billingKeys.balance(),
      queryFn: () => getBillingBalance({ headers: requestHeaders }),
    }),
    queryClient.prefetchQuery({
      queryKey: billingKeys.plans(),
      queryFn: () => getBillingPlans({ headers: requestHeaders }),
    }),
    queryClient.prefetchQuery({
      queryKey: gmailQueryKeys.status(),
      queryFn: () => getTenantStatus({ headers: requestHeaders }),
    }),
  ]);

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <AppShell defaultSidebarOpen={defaultSidebarOpen}>{children}</AppShell>
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
