// Wave 0 RED scaffold — references implementation that lands in Plan 05.
// Locks the segment error.tsx boundary contract (CONTEXT.md D-D2 + D-D3,
// UI-SPEC §Interaction & State Contracts):
//  - renders title + body via next-intl (errors.boundary.{title,body,reset})
//  - NEVER renders error.message or error.stack (privacy contract / Threat T-error-leak)
//  - invokes the unstable_retry prop when CTA clicked (Next 16.2+ — RESEARCH Pitfall 3)
//  - console.error only when NODE_ENV !== 'production'
import { describe, it, expect, vi, afterEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';

// RED-by-design: @/app/(protected)/error does not exist yet (Plan 05 lands it).
import ProtectedError from '@/app/(protected)/error';

const messages = {
  errors: {
    boundary: {
      title: 'Something went wrong',
      body: 'Please try again. If the problem persists, reload the page or sign in again.',
      reset: 'Try again',
    },
  },
} as const;

function renderBoundary(props: { error: Error & { digest?: string }; unstable_retry: () => void }) {
  return render(
    // Cast: the project's strict next-intl typed bundle would force importing
    // the full vi/en.json. The boundary contract under test only exercises
    // errors.boundary.* keys — a minimal local bundle is enough.
    <NextIntlClientProvider locale="en" messages={messages as never}>
      <ProtectedError {...props} />
    </NextIntlClientProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('(protected)/error.tsx boundary', () => {
  it('renders title and body from i18n keys', () => {
    renderBoundary({ error: new Error('boom'), unstable_retry: vi.fn() });
    expect(screen.getByText(/Something went wrong/i)).toBeInTheDocument();
    expect(screen.getByText(/Please try again/i)).toBeInTheDocument();
  });

  it('does NOT render error.message anywhere in DOM', () => {
    const error = new Error('SECRET-PII-LEAK-XXX');
    renderBoundary({ error, unstable_retry: vi.fn() });
    // Privacy: even Error.message can carry tenant fragments / prompt content
    // through ApiError shapes. Boundary must stay opaque.
    expect(screen.queryByText(/SECRET-PII-LEAK-XXX/)).not.toBeInTheDocument();
  });

  it('does NOT render error.stack', () => {
    const error = new Error('innocent message');
    error.stack = 'STACK-XXX-this-must-not-leak';
    renderBoundary({ error, unstable_retry: vi.fn() });
    expect(screen.queryByText(/STACK-XXX-this-must-not-leak/)).not.toBeInTheDocument();
  });

  it('invokes the unstable_retry prop when CTA clicked', () => {
    const unstable_retry = vi.fn();
    renderBoundary({ error: new Error('boom'), unstable_retry });
    fireEvent.click(screen.getByRole('button', { name: /Try again/i }));
    expect(unstable_retry).toHaveBeenCalledTimes(1);
  });

  it('console.error is called when NODE_ENV !== "production"', () => {
    // Plan 05 deviation: Object.defineProperty on process.env throws
    // ERR_INVALID_OBJECT_DEFINE_PROPERTY on modern Node when NODE_ENV is
    // already a non-configurable property (Vitest 4 + Node 24). vi.stubEnv
    // is the supported API and survives across Node versions.
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.stubEnv('NODE_ENV', 'test');
    try {
      renderBoundary({ error: new Error('dev-only-log'), unstable_retry: vi.fn() });
      expect(consoleSpy).toHaveBeenCalled();
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it('console.error is NOT called when NODE_ENV === "production"', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.stubEnv('NODE_ENV', 'production');
    try {
      renderBoundary({ error: new Error('prod-silent'), unstable_retry: vi.fn() });
      expect(consoleSpy).not.toHaveBeenCalled();
    } finally {
      vi.unstubAllEnvs();
    }
  });
});
