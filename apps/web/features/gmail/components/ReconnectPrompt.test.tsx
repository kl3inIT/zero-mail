import { fireEvent, render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { ReconnectPromptGate } from '@/features/gmail/components/ReconnectPrompt';

function renderGate(status: string, ingestionHealth: string = 'HEALTHY') {
  const onReconnect = vi.fn();
  render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      <ReconnectPromptGate
        status={status}
        ingestionHealth={ingestionHealth}
        onReconnect={onReconnect}
      />
    </NextIntlClientProvider>,
  );
  return { onReconnect };
}

describe('ReconnectPrompt - ingestionHealth gate (MAIL-05)', () => {
  it('renders when status is CONNECTED but ingestionHealth is WATCH_UNHEALTHY', () => {
    renderGate('CONNECTED', 'WATCH_UNHEALTHY');

    expect(screen.getByRole('alert')).toHaveTextContent(
      enMessages.connectionHealth.reconnectPrompt,
    );
    expect(
      screen.getByRole('button', { name: enMessages.settings.gmailConnection.reconnectCta }),
    ).toBeInTheDocument();
  });

  it('renders when status is CONNECTED but ingestionHealth is HISTORY_LOST', () => {
    renderGate('CONNECTED', 'HISTORY_LOST');

    expect(screen.getByRole('alert')).toHaveTextContent(
      enMessages.connectionHealth.reconnectPrompt,
    );
  });

  it('renders when status is DISCONNECTED regardless of ingestionHealth', () => {
    const { onReconnect } = renderGate('DISCONNECTED', 'HEALTHY');

    fireEvent.click(
      screen.getByRole('button', { name: enMessages.settings.gmailConnection.reconnectCta }),
    );

    expect(onReconnect).toHaveBeenCalledTimes(1);
  });

  it('does NOT render when status is CONNECTED and ingestionHealth is HEALTHY', () => {
    renderGate('CONNECTED', 'HEALTHY');

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
