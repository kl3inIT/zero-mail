// Wave 0 RED scaffold — locks the zm-theme cookie first-paint dark class contract (Phase 1.6 REQ-1.6-7):
//  - zm-theme=dark cookie causes RootLayout to render <html class="... dark ...">
//  - Absent cookie does NOT apply dark class
//
// RED-by-design: RootLayout does not yet read the zm-theme cookie to apply the dark class.
// This spec becomes GREEN when Phase 1.6 Wave 1 lands.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

const cookieGet = vi.fn();

vi.mock('next/headers', () => ({
  cookies: vi.fn(async () => ({
    get: cookieGet,
    toString: (): string => '',
  })),
  headers: vi.fn(async () => ({ get: vi.fn(() => null) })),
}));

vi.mock('next-intl/server', () => ({
  getLocale: vi.fn(async () => 'en'),
  getMessages: vi.fn(async () => ({})),
}));

import RootLayout from '@/app/layout';

describe('RootLayout zm-theme cookie', () => {
  beforeEach(() => cookieGet.mockReset());

  it('applies dark class when zm-theme=dark', async () => {
    cookieGet.mockImplementation((name: string) => {
      if (name === 'zm-theme') return { value: 'dark' };
      if (name === 'NEXT_LOCALE') return { value: 'en' };
      return undefined;
    });
    const Page = await RootLayout({ children: null as unknown as React.ReactNode });
    const { container } = render(Page as React.ReactElement);
    const html = container.querySelector('html') ?? document.documentElement;
    expect(html.className).toMatch(/\bdark\b/);
  });

  it('does NOT apply dark class when zm-theme is absent', async () => {
    cookieGet.mockImplementation((name: string) =>
      name === 'NEXT_LOCALE' ? { value: 'en' } : undefined,
    );
    const Page = await RootLayout({ children: null as unknown as React.ReactNode });
    const { container } = render(Page as React.ReactElement);
    const html = container.querySelector('html') ?? document.documentElement;
    expect(html.className).not.toMatch(/\bdark\b/);
  });
});
