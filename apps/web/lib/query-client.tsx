'use client';

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import dynamic from 'next/dynamic';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { toast } from 'sonner';

const FIVE_MINUTES_MS = 5 * 60 * 1000;
const THIRTY_MINUTES_MS = 30 * 60 * 1000;
const FALLBACK_ERROR_MESSAGE = 'Có lỗi xảy ra. Vui lòng thử lại.';

const ReactQueryDevtools =
  process.env.NODE_ENV === 'development' && process.env.NEXT_PUBLIC_ENABLE_QUERY_DEVTOOLS === 'true'
    ? dynamic(
        () => import('@tanstack/react-query-devtools').then((module) => module.ReactQueryDevtools),
        { ssr: false },
      )
    : null;

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

function extractFallbackMessage(error: unknown): string {
  if (error && typeof error === 'object') {
    const maybeApiError = error as { code?: unknown; detail?: unknown; message?: unknown };
    if (typeof maybeApiError.code === 'string') return FALLBACK_ERROR_MESSAGE;
    if (typeof maybeApiError.message === 'string' && maybeApiError.message) {
      return maybeApiError.message;
    }
  }
  if (error instanceof Error && error.message) return error.message;
  return FALLBACK_ERROR_MESSAGE;
}

function isHttpClientError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const status = (error as { status?: unknown }).status;
  return typeof status === 'number' && status >= 400 && status < 500;
}

function createAppQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => {
        if (query.meta?.silent) return;
        // TkDodo pattern: initial fetch errors are owned by error.tsx /
        // ErrorBoundary; only background-refetch failures (where the UI
        // already shows cached data) bubble up as a toast.
        if (query.state.data === undefined) return;
        toast.error(query.meta?.errorMessage ?? extractFallbackMessage(error));
      },
    }),
    mutationCache: new MutationCache({
      onError: (error, _variables, _context, mutation) => {
        if (mutation.meta?.silent || !mutation.meta?.errorMessage) return;
        toast.error(mutation.meta.errorMessage);
      },
      onSuccess: (_data, _variables, _context, mutation) => {
        if (mutation.meta?.silent || !mutation.meta?.successMessage) return;
        toast.success(mutation.meta.successMessage);
      },
    }),
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => failureCount < 1 && !isHttpClientError(error),
        staleTime: FIVE_MINUTES_MS,
        gcTime: THIRTY_MINUTES_MS,
      },
      mutations: {
        retry: false,
      },
    },
  });
}

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(createAppQueryClient);
  return (
    <QueryClientProvider client={client}>
      {children}
      {ReactQueryDevtools ? <ReactQueryDevtools initialIsOpen={false} /> : null}
    </QueryClientProvider>
  );
}
