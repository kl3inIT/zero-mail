// Locks the root layout font contract (Phase 1.6 REQ-1.6-2):
//  - root layout stays system-font backed so mobile Lighthouse has stable >90 performance
//  - decorative mono/serif faces stay system-backed too
//
// Guards the Phase 1.6 Wave 1 font wiring in RootLayout.
import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';

vi.mock('next/headers', () => ({
  cookies: vi.fn(async () => ({
    get: vi.fn((name: string) => (name === 'NEXT_LOCALE' ? { value: 'en' } : undefined)),
    toString: (): string => '',
  })),
  headers: vi.fn(async () => ({ get: vi.fn(() => null) })),
}));

vi.mock('next-intl/server', () => ({
  getLocale: vi.fn(async () => 'en'),
  getMessages: vi.fn(async () => ({})),
  getTranslations: vi.fn(async () => (key: string) => key),
}));

import RootLayout from '@/app/layout';

describe('RootLayout fonts', () => {
  it('html className does not include next/font CSS-variable classes', async () => {
    const Page = await RootLayout({ children: null as unknown as React.ReactNode });
    const { container } = render(Page as React.ReactElement);
    const html = container.querySelector('html') ?? document.documentElement;
    const cls = html.className ?? '';
    expect(cls).not.toMatch(/--font-be-vietnam-pro/);
    expect(cls).not.toMatch(/--font-geist/);
    expect(cls).not.toMatch(/--font-instrument-serif/);
  });
});
