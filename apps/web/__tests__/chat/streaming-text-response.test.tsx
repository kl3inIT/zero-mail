import { act, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { StreamingTextResponse } from '@/features/chat/components/streaming-text-response';

vi.mock('@/components/ai/response', () => ({
  Response: ({ children, isAnimating }: { children: ReactNode; isAnimating?: boolean }) => (
    <div data-animating={String(Boolean(isAnimating))} data-testid="response">
      {children}
    </div>
  ),
}));

describe('StreamingTextResponse', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('reveals live assistant text incrementally even when the full target arrives at once', async () => {
    vi.useFakeTimers();
    const targetText = 'Xin chao tu Zero Mail. Day la cau tra loi dang stream.';

    render(<StreamingTextResponse isStreaming={false} revealIncrementally text={targetText} />);

    const response = screen.getByTestId('response');
    expect(response).toHaveTextContent('');
    expect(response).toHaveAttribute('data-animating', 'true');

    await act(async () => {
      vi.advanceTimersByTime(16);
    });

    const firstVisibleText = response.textContent ?? '';
    expect(firstVisibleText.length).toBeGreaterThan(0);
    expect(firstVisibleText).not.toBe(targetText);

    for (let tick = 0; tick < 80; tick += 1) {
      await act(async () => {
        vi.advanceTimersByTime(16);
      });
    }

    expect(response).toHaveTextContent(targetText);
    expect(response).toHaveAttribute('data-animating', 'false');
  });

  it('renders persisted text immediately', () => {
    const targetText = 'Persisted history text should not replay.';

    render(
      <StreamingTextResponse isStreaming={false} revealIncrementally={false} text={targetText} />,
    );

    expect(screen.getByTestId('response')).toHaveTextContent(targetText);
  });
});
