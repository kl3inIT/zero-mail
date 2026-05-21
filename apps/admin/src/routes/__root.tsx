import { createRootRouteWithContext } from '@tanstack/react-router';
import type { QueryClient } from '@tanstack/react-query';

import {
  RootErrorScreen,
  RootNotFoundScreen,
  RootPendingScreen,
  RootShell,
} from '@/components/RootShell';

type RouterContext = {
  queryClient: QueryClient;
};

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootShell,
  errorComponent: RootErrorScreen,
  notFoundComponent: RootNotFoundScreen,
  pendingComponent: RootPendingScreen,
});
