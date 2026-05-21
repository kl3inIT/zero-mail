import type { QueryClient } from '@tanstack/react-query';
import { createFileRoute, redirect } from '@tanstack/react-router';

import { AdminLayout } from '@/components/AdminLayout';
import { getCurrentAdmin } from '@/lib/admin-session';

type RouterContext = {
  queryClient: QueryClient;
};

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: async ({ context }) => {
    const routerContext = context as RouterContext;
    try {
      const admin = await routerContext.queryClient.ensureQueryData({
        queryKey: ['admin-me'],
        queryFn: getCurrentAdmin,
      });
      return { admin };
    } catch {
      throw redirect({ to: '/login' });
    }
  },
  component: AuthenticatedLayoutRoute,
});

function AuthenticatedLayoutRoute() {
  const { admin } = Route.useRouteContext();
  return <AdminLayout admin={admin} />;
}
