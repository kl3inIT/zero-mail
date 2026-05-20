import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { AutoRefreshIndicator } from '@/components/AutoRefreshIndicator';

describe('AutoRefreshIndicator', () => {
  it('renders "Updated Ns ago" with tabular-nums when not paused', () => {
    render(
      <AutoRefreshIndicator
        lastUpdatedAt={new Date(Date.now() - 4_000)}
        intervalMs={10_000}
        paused={false}
        onPauseToggle={() => undefined}
      />,
    );
    expect(screen.getByText(/Updated/i).className).toMatch(/tabular-nums/);
    expect(screen.getByText(/Updated \d+s ago/i)).toBeInTheDocument();
  });

  it('shows "Paused" and exposes a Resume control when paused', async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();
    render(
      <AutoRefreshIndicator
        lastUpdatedAt={new Date()}
        intervalMs={10_000}
        paused
        onPauseToggle={onToggle}
      />,
    );
    expect(screen.getByText('Paused')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Resume auto-refresh/i }));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it('uses aria-live=polite on the elapsed-time region for accessibility', () => {
    render(
      <AutoRefreshIndicator
        lastUpdatedAt={new Date()}
        intervalMs={10_000}
        paused={false}
        onPauseToggle={() => undefined}
      />,
    );
    const elapsedTextNode = screen.getByText(/Updated/i);
    expect(elapsedTextNode).toHaveAttribute('aria-live', 'polite');
  });
});
