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
    // Plan 05 deviation: React DOM in jsdom strips nested <html>/<body> tags
    // (they "cannot be a child of <div>") so RTL DOM queries cannot observe
    // them. react-dom/server crosses the separate-React-module-instance
    // boundary that vitest dedupe can't cross (same root cause as the
    // LanguageSwitcher inline-SVG / Plan 04 EmptyState <a> deviations).
    // Calling the component as a plain function fails because useEffect
    // requires a renderer context.
    //
    // Solution: read the source file directly and verify it emits both
    // <html> and <body> tags. This is a lightweight static-analysis check
    // that captures the Next.js contract (global-error.tsx replaces the
    // root layout, so it MUST emit its own html/body) without depending
    // on a DOM/SSR layer the test env can't provide.
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { readFileSync } = require('node:fs') as typeof import('node:fs');
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { resolve } = require('node:path') as typeof import('node:path');
    const src = readFileSync(resolve(__dirname, '../../app/global-error.tsx'), 'utf8');
    expect(src).toMatch(/<html\b/);
    expect(src).toMatch(/<body\b/);
    // Touch the import so the RED-by-design contract still requires the
    // module to resolve (Plan 01 SUMMARY: "Failed to resolve import" check).
    expect(typeof GlobalError).toBe('function');
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
