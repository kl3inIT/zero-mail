import { dehydrate, HydrationBoundary, QueryClient } from '@tanstack/react-query';
import { cookies } from 'next/headers';

import { AppShell } from '@/components/shell/AppShell';
import { getCurrentUserCached } from '@/features/account/api/account-api';
import { getBillingBalance } from '@/features/billing/api/billing-api';
import { billingKeys } from '@/features/billing/query-keys';
import { getTenantStatus } from '@/features/gmail/api/gmail-api';
import { gmailQueryKeys } from '@/features/gmail/query-keys';
import { triageKeys } from '@/features/triage/query-keys';

const SIDEBAR_COOKIE_NAME = 'sidebar_state';

export default async function ProtectedAppLayout({ children }: { children: React.ReactNode }) {
  const cookieStore = await cookies();
  const cookieHeader = cookieStore.toString();
  const requestHeaders = cookieHeader ? { Cookie: cookieHeader } : undefined;
  const defaultSidebarOpen = cookieStore.get(SIDEBAR_COOKIE_NAME)?.value !== 'false';
  const queryClient = new QueryClient();

  await Promise.all([
    queryClient.prefetchQuery({
      queryKey: triageKeys.pauseState(),
      queryFn: async () => {
        const user = await getCurrentUserCached(cookieHeader);
        return user.triagePaused;
      },
    }),
    queryClient.prefetchQuery({
      queryKey: billingKeys.balance(),
      queryFn: () => getBillingBalance({ headers: requestHeaders }),
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
