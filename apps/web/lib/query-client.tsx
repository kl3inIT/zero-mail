'use client';

import { createSyncStoragePersister } from '@tanstack/query-sync-storage-persister';
import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { PersistQueryClientProvider, type Persister } from '@tanstack/react-query-persist-client';
import dynamic from 'next/dynamic';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { toast } from 'sonner';

const FIVE_MINUTES_MS = 5 * 60 * 1000;
const THIRTY_MINUTES_MS = 30 * 60 * 1000;
const TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000;
const FALLBACK_ERROR_MESSAGE = 'Có lỗi xảy ra. Vui lòng thử lại.';

// localStorage key used by the persist plugin. Exported so the logout flow
// can wipe it after queryClient.clear() — otherwise the next user on this
// browser would rehydrate the prior user's cached UI projections.
export const PERSIST_STORAGE_KEY = 'zm:rq-cache:v1';

// Buster — bump when the dehydrate shape changes (renamed query keys, new
// privacy excludes, etc.). Stale persisted entries with a different buster
// are discarded on hydrate.
const PERSIST_BUSTER = 'zm-v2-unsub-no-candidates';

const ReactQueryDevtools =
  process.env.NODE_ENV === 'development' && process.env.NEXT_PUBLIC_ENABLE_QUERY_DEVTOOLS === 'true'
    ? dynamic(
        () => import('@tanstack/react-query-devtools').then((module) => module.ReactQueryDevtools),
        { ssr: false },
      )
    : null;

type MutationToastContext = {
  data?: unknown;
  error?: unknown;
  variables: unknown;
};

type MutationToastValue = string | ((toastContext: MutationToastContext) => string | undefined);

interface AppQueryMeta extends Record<string, unknown> {
  errorMessage?: string;
  silent?: boolean;
}

interface AppMutationMeta extends Record<string, unknown> {
  successMessage?: MutationToastValue;
  successDescription?: MutationToastValue;
  errorMessage?: MutationToastValue;
  errorDescription?: MutationToastValue;
  silent?: boolean;
}

declare module '@tanstack/react-query' {
  interface Register {
    queryMeta: AppQueryMeta;
    mutationMeta: AppMutationMeta;
  }
}

function resolveMutationToastValue(
  value: MutationToastValue | undefined,
  toastContext: MutationToastContext,
): string | undefined {
  if (!value) return undefined;
  return typeof value === 'function' ? value(toastContext) : value;
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
      onError: (error, variables, _context, mutation) => {
        if (mutation.meta?.silent) return;
        const toastContext = { error, variables };
        const errorMessage = resolveMutationToastValue(mutation.meta?.errorMessage, toastContext);
        if (!errorMessage) return;
        toast.error(errorMessage, {
          description: resolveMutationToastValue(mutation.meta?.errorDescription, toastContext),
        });
      },
      onSuccess: (data, variables, _context, mutation) => {
        if (mutation.meta?.silent) return;
        const toastContext = { data, variables };
        const successMessage = resolveMutationToastValue(
          mutation.meta?.successMessage,
          toastContext,
        );
        if (!successMessage) return;
        toast.success(successMessage, {
          description: resolveMutationToastValue(mutation.meta?.successDescription, toastContext),
        });
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

// Whitelist of query-key shapes that are safe to persist to localStorage.
// Privacy invariant (CLAUDE.md): no raw email bodies / snippets in long-term
// storage. Sender headers + aggregate counts are OK; per-message subject+
// snippet and message body are not.
function shouldDehydrateUnsubscribeQuery(queryKey: readonly unknown[]): boolean {
  if (queryKey[0] !== 'cleanup' || queryKey[1] !== 'unsubscribe-campaign') return false;
  // ['cleanup', 'unsubscribe-campaign', 'candidates', spec, limit] — sender emails
  // + counts only, so privacy-wise it would be OK to persist. We deliberately do
  // NOT, because this query drives the SSR-rendered candidates table: the server
  // has no localStorage and renders the loading skeleton, but restoring persisted
  // candidates on the client makes the first client render show the <table> — a
  // hydration mismatch (server skeleton vs client table). Keeping it memory-only
  // restores SSR/client symmetry; the 5-min staleTime + keepPreviousData still
  // avoid re-fetch on in-session navigation, and a hard reload simply re-fetches.
  if (queryKey[2] === 'candidates') return false;
  // ['cleanup', 'unsubscribe-campaign', 'stats', 'timeline', email, days] —
  // {date, count}[] aggregates. OK.
  if (queryKey[2] === 'stats' && queryKey[3] === 'timeline') return true;
  // 'stats/messages' (subject+snippet) and 'stats/body' (full body) stay
  // memory-only — body content must not persist.
  return false;
}

// Storage proxy that lazily resolves window.localStorage. Created once at
// module load — both server and client get the SAME persister instance, so
// PersistQueryClientProvider's internal useState/useId calls produce
// identical sequences on SSR and first client render. Without this, the
// `useState(createPersister)` initializer returned a no-op stub on the
// server and a real persister on the client; the differing prop identity
// flowed into base-ui's useId counter via PersistQueryClientProvider's
// internals and produced base-ui hydration mismatches on every dropdown.
const lazyLocalStorage: Storage = {
  get length() {
    return typeof window === 'undefined' ? 0 : window.localStorage.length;
  },
  clear() {
    if (typeof window !== 'undefined') window.localStorage.clear();
  },
  getItem(key) {
    return typeof window === 'undefined' ? null : window.localStorage.getItem(key);
  },
  key(index) {
    return typeof window === 'undefined' ? null : window.localStorage.key(index);
  },
  removeItem(key) {
    if (typeof window !== 'undefined') window.localStorage.removeItem(key);
  },
  setItem(key, value) {
    if (typeof window !== 'undefined') window.localStorage.setItem(key, value);
  },
};

const sharedPersister: Persister = createSyncStoragePersister({
  storage: lazyLocalStorage,
  key: PERSIST_STORAGE_KEY,
  throttleTime: 1000,
});

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(createAppQueryClient);

  return (
    <PersistQueryClientProvider
      client={client}
      persistOptions={{
        persister: sharedPersister,
        maxAge: TWENTY_FOUR_HOURS_MS,
        buster: PERSIST_BUSTER,
        dehydrateOptions: {
          // Persist only successful queries on the unsubscribe-campaign
          // allowlist. Everything else (and all failures) stay memory-only.
          shouldDehydrateQuery: (query) =>
            query.state.status === 'success' && shouldDehydrateUnsubscribeQuery(query.queryKey),
        },
      }}
    >
      {children}
      {ReactQueryDevtools ? <ReactQueryDevtools initialIsOpen={false} /> : null}
    </PersistQueryClientProvider>
  );
}
