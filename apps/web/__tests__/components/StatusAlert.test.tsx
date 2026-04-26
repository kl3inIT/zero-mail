// Wave 0 RED scaffold — references implementation that lands in Plan 04.
// Locks the StatusAlert primitive contract (UI-SPEC §Component Inventory + §Color):
//  - cva variants: info / success / warn / error
//  - i18n keys via titleKey + bodyKey (NEVER raw strings)
//  - destructive token classes for error variant (no raw color names)
//  - optional cta with onClick
//  - ICU params via useTranslations
import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';

// RED-by-design: @/components/ui/StatusAlert does not exist yet (Plan 04 lands it).
import { StatusAlert } from '@/components/ui/StatusAlert';

const messages = {
  errors: {
    boundary: {
      title: 'Something went wrong',
      body: 'Please try again. If the problem persists, reload the page or sign in again.',
      reset: 'Try again',
    },
  },
  greeting: {
    hello: 'Hello, {name}',
  },
} as const;

function renderWithIntl(node: React.ReactNode) {
  return render(
    // Cast: the project's strict next-intl typed bundle would force importing
    // the full vi/en.json. The primitive contract under test only exercises a
    // narrow key set (errors.boundary.*, greeting.*) — a local bundle is enough.
    <NextIntlClientProvider locale="en" messages={messages as never}>
      {node}
    </NextIntlClientProvider>,
  );
}

describe('StatusAlert', () => {
  it('renders title and body from i18n keys for variant=info', () => {
    renderWithIntl(
      <StatusAlert
        variant="info"
        titleKey="errors.boundary.title"
        bodyKey="errors.boundary.body"
      />,
    );
    expect(screen.getByText(/Something went wrong/i)).toBeInTheDocument();
    expect(screen.getByText(/Please try again/i)).toBeInTheDocument();
  });

  it('renders error variant with destructive token classes (no raw color names)', () => {
    const { container } = renderWithIntl(
      <StatusAlert
        variant="error"
        titleKey="errors.boundary.title"
        bodyKey="errors.boundary.body"
      />,
    );
    const root = container.querySelector('[data-slot="alert"]');
    expect(root).not.toBeNull();
    const className = root!.className;
    // Token-aware contract per UI-SPEC §Color: error variant uses --destructive
    // tokens, NEVER raw color names like text-red-* / bg-red-* / border-red-*.
    expect(className).toMatch(/destructive/);
    expect(className).not.toMatch(/text-red-|bg-red-|border-red-|text-stone-/);
  });

  it('invokes cta.onClick when CTA button clicked', () => {
    const onClick = vi.fn();
    renderWithIntl(
      <StatusAlert
        variant="error"
        titleKey="errors.boundary.title"
        bodyKey="errors.boundary.body"
        cta={{ labelKey: 'errors.boundary.reset', onClick }}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /Try again/i }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('does not render body section when bodyKey is undefined', () => {
    renderWithIntl(<StatusAlert variant="info" titleKey="errors.boundary.title" />);
    // The body text from messages.errors.boundary.body is not rendered.
    expect(screen.queryByText(/Please try again/i)).not.toBeInTheDocument();
  });

  it('passes ICU params to translation', () => {
    renderWithIntl(
      <StatusAlert variant="info" titleKey="greeting.hello" params={{ name: 'Alice' }} />,
    );
    expect(screen.getByText(/Hello, Alice/i)).toBeInTheDocument();
  });
});
