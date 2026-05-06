// Locks the not-found.tsx contract (CONTEXT.md D-D2, UI-SPEC Copywriting Contract):
//  - renders title from errors.notFound.title (i18n)
//  - renders body from errors.notFound.body
//  - renders Back home link pointing to "/"
//
// not-found.tsx is a server component (default async export). For Vitest we call
// the component as a function and pass the returned JSX into RTL's render under
// a NextIntlClientProvider with the same messages bundle that getTranslations()
// would resolve at runtime.
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';

// next-intl/server's getTranslations is the server-only API; mock it to read
// from the same flat messages object the Provider hosts so behavior matches
// production.
vi.mock('next-intl/server', () => ({
  getTranslations: async (namespace?: string) => {
    const messages = {
      errors: {
        notFound: {
          title: 'Page not found',
          body: "The page you're looking for doesn't exist or has been moved.",
          backHome: 'Back home',
        },
      },
    } as const;
    return (key: string) => {
      if (namespace === 'errors.notFound') {
        const ns = messages.errors.notFound as Record<string, string>;
        return ns[key] ?? key;
      }
      // Fallback: dotted key from root.
      const parts = key.split('.');
      let cur: unknown = messages;
      for (const p of parts) {
        if (cur && typeof cur === 'object' && p in (cur as Record<string, unknown>)) {
          cur = (cur as Record<string, unknown>)[p];
        } else {
          return key;
        }
      }
      return typeof cur === 'string' ? cur : key;
    };
  },
}));

import NotFound from '@/app/not-found';

const messages = {
  errors: {
    notFound: {
      title: 'Page not found',
      body: "The page you're looking for doesn't exist or has been moved.",
      backHome: 'Back home',
    },
  },
} as const;

async function renderNotFound() {
  // not-found.tsx is async — await the component, then render its tree.
  const tree = await NotFound();
  return render(
    // Cast: avoids forcing the full strict bundle import (see error.test.tsx).
    <NextIntlClientProvider locale="en" messages={messages as never}>
      {tree}
    </NextIntlClientProvider>,
  );
}

describe('app/not-found.tsx', () => {
  it('renders title from errors.notFound.title', async () => {
    await renderNotFound();
    expect(screen.getByText(/Page not found/i)).toBeInTheDocument();
  });

  it('renders body from errors.notFound.body', async () => {
    await renderNotFound();
    expect(screen.getByText(/doesn't exist or has been moved/i)).toBeInTheDocument();
  });

  it('renders Back home link pointing to "/"', async () => {
    await renderNotFound();
    const link = screen.getByRole('link', { name: /Back home/i });
    expect(link).toHaveAttribute('href', '/');
  });
});
