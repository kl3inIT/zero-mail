// Phase 12 W3 Task 2 — Vitest contract test for the useCalendarConnections hook.
//
// Mocks the underlying calendar-api module + global fetch so the hook exercises
// its real TanStack Query plumbing without hitting the network. Validates the
// observable outcome (data flowing into the component) per TESTING.md §2 rather
// than implementation details (mutationFn call counts).
import { describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { PropsWithChildren } from 'react';

import { useCalendarConnections } from '@/features/calendar/hooks/use-calendar-connections';

const MAILBOX_ID = '00000000-0000-0000-0000-00000000bbbb';

const STUB_CONNECTIONS = [
  {
    id: '00000000-0000-0000-0000-00000000cccc',
    googleEmail: 'founder@example.com',
    status: 'CONNECTED' as const,
    connectedAt: '2026-06-20T10:00:00Z',
    disconnectedAt: null,
    googleProfileName: 'Founder',
    googleProfilePictureUrl: null,
    calendars: [
      {
        id: '00000000-0000-0000-0000-00000000dddd',
        externalCalendarId: 'primary',
        name: 'Primary',
        isPrimary: true,
        isEnabled: true,
        timezone: 'UTC',
      },
    ],
    preferences: [
      {
        id: '00000000-0000-0000-0000-00000000eeee',
        mailboxId: MAILBOX_ID,
        calendarConnectionId: '00000000-0000-0000-0000-00000000cccc',
        calendarId: '00000000-0000-0000-0000-00000000dddd',
        role: 'FREEBUSY' as const,
      },
    ],
  },
];

vi.mock('@/features/calendar/api/calendar-api', () => ({
  listCalendarConnections: vi.fn(async () => STUB_CONNECTIONS),
}));

function createWrapper(client: QueryClient) {
  return function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

describe('useCalendarConnections', () => {
  it('returns the mocked calendar-connection list for the given mailbox', async () => {
    const client = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
        mutations: { retry: false },
      },
    });

    const { result } = renderHook(() => useCalendarConnections(MAILBOX_ID), {
      wrapper: createWrapper(client),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].googleEmail).toBe('founder@example.com');
    expect(result.current.data?.[0].status).toBe('CONNECTED');
    expect(result.current.data?.[0].calendars[0].isPrimary).toBe(true);
    expect(result.current.data?.[0].preferences[0].role).toBe('FREEBUSY');
  });

  it('disables the query when mailboxId is empty', () => {
    const client = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
        mutations: { retry: false },
      },
    });

    const { result } = renderHook(() => useCalendarConnections(''), {
      wrapper: createWrapper(client),
    });

    // {enabled: false} ⇒ fetchStatus stays idle and data stays undefined.
    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.data).toBeUndefined();
  });
});
