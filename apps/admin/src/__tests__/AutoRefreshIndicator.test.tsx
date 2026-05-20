import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { AutoRefreshIndicator } from '@/components/AutoRefreshIndicator';

describe('AutoRefreshIndicator', () => {
  it('renders the elapsed-time line with tabular-nums when not paused', () => {
    render(
      <AutoRefreshIndicator
        lastUpdatedAt={new Date(Date.now() - 4_000)}
        intervalMs={10_000}
        paused={false}
        onPauseToggle={() => undefined}
      />,
    );
    expect(screen.getByText(/Cập nhật cách đây/i).className).toMatch(/tabular-nums/);
    expect(screen.getByText(/Cập nhật cách đây \d+s/i)).toBeInTheDocument();
  });

  it('shows the paused label and exposes a resume control when paused', async () => {
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
    expect(screen.getByText('Đã tạm dừng')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Tiếp tục tự làm mới/i }));
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
    const elapsedTextNode = screen.getByText(/Cập nhật cách đây/i);
    expect(elapsedTextNode).toHaveAttribute('aria-live', 'polite');
  });
});
