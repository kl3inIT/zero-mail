import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { TooltipProvider } from '@/components/ui/tooltip';
import type { NotificationPreferencesResponse } from '@/features/notifications/api/notifications-api';
import { notificationsKeys } from '@/features/notifications/query-keys';
import enMessages from '@/i18n/messages/en.json';

const mocks = vi.hoisted(() => ({
  fetchNotificationPreferences: vi.fn(),
  updateNotificationPreferences: vi.fn(),
  toastError: vi.fn(),
  toastSuccess: vi.fn(),
}));

vi.mock('@/features/notifications/api/notifications-api', () => ({
  fetchNotificationPreferences: mocks.fetchNotificationPreferences,
  updateNotificationPreferences: mocks.updateNotificationPreferences,
}));

vi.mock('sonner', () => ({
  toast: {
    error: mocks.toastError,
    success: mocks.toastSuccess,
  },
}));

vi.mock('@/components/ui/switch', () => ({
  Switch: ({
    checked,
    disabled,
    onCheckedChange,
    ...props
  }: {
    checked?: boolean;
    disabled?: boolean;
    onCheckedChange?: (checked: boolean) => void;
    [key: string]: unknown;
  }) => (
    <button
      type="button"
      role="switch"
      aria-checked={Boolean(checked)}
      disabled={disabled}
      onClick={() => onCheckedChange?.(!checked)}
      {...props}
    />
  ),
}));

vi.mock('@/components/ui/select', async () => {
  const React = await import('react');
  type SelectContextValue = {
    value: string;
    disabled?: boolean;
    onValueChange?: (value: string) => void;
  };
  const SelectContext = React.createContext<SelectContextValue>({
    value: '',
  });

  return {
    Select: ({
      value,
      disabled,
      onValueChange,
      children,
    }: SelectContextValue & { children: React.ReactNode }) => (
      <SelectContext.Provider value={{ value, disabled, onValueChange }}>
        <div>{children}</div>
      </SelectContext.Provider>
    ),
    SelectContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    SelectGroup: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    SelectItem: ({ value, children }: { value: string; children: React.ReactNode }) => {
      const context = React.useContext(SelectContext);
      return (
        <button
          type="button"
          role="option"
          aria-selected={context.value === value}
          onClick={() => context.onValueChange?.(value)}
        >
          {children}
        </button>
      );
    },
    SelectTrigger: ({
      children,
      disabled,
      ...props
    }: {
      children: React.ReactNode;
      disabled?: boolean;
      [key: string]: unknown;
    }) => {
      const context = React.useContext(SelectContext);
      return (
        <button type="button" disabled={disabled || context.disabled} {...props}>
          {children}
        </button>
      );
    },
    SelectValue: () => {
      const context = React.useContext(SelectContext);
      return <span>{`${context.value.padStart(2, '0')}:00`}</span>;
    },
  };
});

import { NotificationsSection } from '@/features/notifications/components/NotificationsSection';

describe('NotificationsSection', () => {
  beforeEach(() => {
    mocks.fetchNotificationPreferences.mockReset();
    mocks.updateNotificationPreferences.mockReset();
    mocks.toastError.mockReset();
    mocks.toastSuccess.mockReset();
  });

  it('optimistically toggles digest email and rolls back on error', async () => {
    const initialPreferences = preferences({ digestEnabled: true, digestSendHourLocal: 20 });
    const updateAttempt = deferred<NotificationPreferencesResponse>();
    mocks.fetchNotificationPreferences.mockResolvedValue(initialPreferences);
    mocks.updateNotificationPreferences.mockReturnValue(updateAttempt.promise);

    renderWithProviders(<NotificationsSection />, initialPreferences);

    const switchControl = screen.getByTestId('daily-digest-switch');
    expect(switchControl).toBeChecked();

    fireEvent.click(switchControl);

    await waitFor(() => expect(mocks.updateNotificationPreferences).toHaveBeenCalled());
    expect(mocks.updateNotificationPreferences.mock.calls[0]?.[0]).toEqual({
      digestEnabled: false,
      digestSendHourLocal: 20,
    });
    await waitFor(() => expect(screen.getByTestId('daily-digest-switch')).not.toBeChecked());

    updateAttempt.reject(new Error('save failed'));

    await waitFor(() => expect(screen.getByTestId('daily-digest-switch')).toBeChecked());
    expect(mocks.toastError).toHaveBeenCalledWith(
      "Couldn't save — Try again",
      expect.objectContaining({
        action: expect.objectContaining({ label: 'Retry' }),
      }),
    );
  });

  it('optimistically persists send-hour changes', async () => {
    const initialPreferences = preferences({ digestEnabled: true, digestSendHourLocal: 20 });
    const updateAttempt = deferred<NotificationPreferencesResponse>();
    mocks.fetchNotificationPreferences.mockResolvedValue(initialPreferences);
    mocks.updateNotificationPreferences.mockReturnValue(updateAttempt.promise);

    renderWithProviders(<NotificationsSection />, initialPreferences);

    fireEvent.click(screen.getByRole('option', { name: '08:00' }));

    await waitFor(() => expect(mocks.updateNotificationPreferences).toHaveBeenCalled());
    expect(mocks.updateNotificationPreferences.mock.calls[0]?.[0]).toEqual({
      digestEnabled: true,
      digestSendHourLocal: 8,
    });
    await waitFor(() => {
      expect(screen.getByTestId('digest-send-hour-select')).toHaveTextContent('08:00');
    });
  });

  it('keeps the send-hour select mounted but disabled when digest is off', () => {
    renderWithProviders(
      <NotificationsSection />,
      preferences({ digestEnabled: false, digestSendHourLocal: 9 }),
    );

    const selectTrigger = screen.getByTestId('digest-send-hour-select');
    expect(selectTrigger).toBeInTheDocument();
    expect(selectTrigger).toBeDisabled();
    expect(selectTrigger).toHaveTextContent('09:00');
  });
});

function renderWithProviders(
  children: ReactNode,
  initialPreferences: NotificationPreferencesResponse,
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData(notificationsKeys.preferences(), initialPreferences);

  return render(
    <QueryClientProvider client={queryClient}>
      <NextIntlClientProvider locale="en" messages={enMessages}>
        <TooltipProvider>{children}</TooltipProvider>
      </NextIntlClientProvider>
    </QueryClientProvider>,
  );
}

function preferences(
  overrides: Partial<NotificationPreferencesResponse> = {},
): NotificationPreferencesResponse {
  return {
    channel: 'DAILY_DIGEST',
    digestEnabled: true,
    digestSendHourLocal: 20,
    timeZone: 'Asia/Ho_Chi_Minh',
    ...overrides,
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((innerResolve, innerReject) => {
    resolve = innerResolve;
    reject = innerReject;
  });
  return { promise, resolve, reject };
}
