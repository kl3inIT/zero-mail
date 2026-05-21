import {
  MutationCache,
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from '@tanstack/react-query';
import { createRouter, RouterProvider } from '@tanstack/react-router';
import React from 'react';
import ReactDOM from 'react-dom/client';
import { toast } from 'sonner';

import { extractErrorMessage } from './lib/api/admin-errors';
import { routeTree } from './routeTree.gen';
import './styles/globals.css';

declare module '@tanstack/react-query' {
  interface Register {
    queryMeta: {
      errorMessage?: string;
      silent?: boolean;
    };
    mutationMeta: {
      successMessage?: string;
      errorMessage?: string;
      silent?: boolean;
    };
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error, query) => {
      if (query.meta?.silent) return;
      if (query.state.data === undefined) return;
      toast.error(query.meta?.errorMessage ?? extractErrorMessage(error));
    },
  }),
  mutationCache: new MutationCache({
    onError: (error, _variables, _context, mutation) => {
      if (mutation.meta?.silent) return;
      toast.error(mutation.meta?.errorMessage ?? extractErrorMessage(error));
    },
    onSuccess: (_data, _variables, _context, mutation) => {
      if (mutation.meta?.silent || !mutation.meta?.successMessage) return;
      toast.success(mutation.meta.successMessage);
    },
  }),
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
    mutations: {
      retry: false,
    },
  },
});

const router = createRouter({
  routeTree,
  context: {
    queryClient,
  },
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </React.StrictMode>,
);
