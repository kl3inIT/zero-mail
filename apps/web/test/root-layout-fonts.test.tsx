// Wave 0 RED scaffold — locks the root layout font CSS-variable contract (Phase 1.6 REQ-1.6-2):
//  - <html> className contains all 4 font CSS-variable class substrings
//  - Be Vietnam Pro + Instrument Serif declared via next/font/google
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
}));

vi.mock('next/font/google', () => {
  const font = (options: { variable: string }) => ({ variable: options.variable });
  return {
    Be_Vietnam_Pro: font,
    Geist: font,
    Geist_Mono: font,
    Instrument_Serif: font,
  };
});

import RootLayout from '@/app/layout';

describe('RootLayout fonts', () => {
  it('html className includes all 4 font CSS-variable classes', async () => {
    const Page = await RootLayout({ children: null as unknown as React.ReactNode });
    const { container } = render(Page as React.ReactElement);
    const html = container.querySelector('html') ?? document.documentElement;
    const cls = html.className ?? '';
    expect(cls).toMatch(/--font-geist-sans/);
    expect(cls).toMatch(/--font-geist-mono/);
    expect(cls).toMatch(/--font-be-vietnam-pro/);
    expect(cls).toMatch(/--font-instrument-serif/);
  });
});
