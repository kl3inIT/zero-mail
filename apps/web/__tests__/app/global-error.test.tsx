// Wave 0 RED scaffold — references implementation that lands in Plan 05.
// Locks the global-error.tsx contract (CONTEXT.md D-D2 + D-D3, UI-SPEC):
//  - renders own <html>/<body> (replaces root layout when root itself throws)
//  - English-only fallback (no next-intl provider available)
//  - NEVER renders error.message
//  - invokes unstable_retry when reload button clicked (Next 16.2+)
//  - falls back to window.location.reload() when unstable_retry throws
//    (Plan 05 Task 1 File 1 contract — full reload is the only safe escape from
//     a broken root replacement)
import { describe, it, expect, vi, afterEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

// RED-by-design: @/app/global-error does not exist yet (Plan 05 lands it).
import GlobalError from '@/app/global-error';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('global-error.tsx', () => {
  it('renders English-only fallback copy without next-intl provider', () => {
    render(<GlobalError error={new Error('boom')} unstable_retry={vi.fn()} />);
    // CONTEXT D-D3 + UI-SPEC §Copywriting Contract: hardcoded English copy.
    expect(screen.getByText(/Something went wrong/i)).toBeInTheDocument();
  });

  it('renders own <body> element (root replacement)', () => {
    const { container } = render(
      <GlobalError error={new Error('boom')} unstable_retry={vi.fn()} />,
    );
    // The component must include its own <html>/<body> per Next 16 conventions.
    // jsdom always has a top-level <body>, so we assert the rendered subtree
    // contains an html or body node (i.e., the component truly emits one).
    const html = container.querySelector('html');
    const body = container.querySelector('body');
    expect(html ?? body).not.toBeNull();
  });

  it('does NOT render error.message', () => {
    const error = new Error('SECRET-GLOBAL-LEAK-XXX');
    render(<GlobalError error={error} unstable_retry={vi.fn()} />);
    expect(screen.queryByText(/SECRET-GLOBAL-LEAK-XXX/)).not.toBeInTheDocument();
  });

  it('invokes unstable_retry when reload button clicked', () => {
    const unstable_retry = vi.fn();
    render(<GlobalError error={new Error('boom')} unstable_retry={unstable_retry} />);
    fireEvent.click(screen.getByRole('button', { name: /Try again|Reload/i }));
    expect(unstable_retry).toHaveBeenCalledTimes(1);
  });

  it('falls back to window.location.reload when unstable_retry throws', () => {
    const reloadSpy = vi.fn();
    // jsdom's window.location.reload is non-configurable in some versions; replace
    // the entire location object so the spy survives userEvent.click.
    const originalLocation = window.location;
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: { ...originalLocation, reload: reloadSpy, href: originalLocation.href },
    });

    const unstable_retry = vi.fn(() => {
      throw new Error('retry-broken');
    });

    try {
      render(<GlobalError error={new Error('boom')} unstable_retry={unstable_retry} />);
      fireEvent.click(screen.getByRole('button', { name: /Try again|Reload/i }));
      expect(unstable_retry).toHaveBeenCalledTimes(1);
      expect(reloadSpy).toHaveBeenCalledTimes(1);
    } finally {
      Object.defineProperty(window, 'location', {
        configurable: true,
        writable: true,
        value: originalLocation,
      });
    }
  });
});
