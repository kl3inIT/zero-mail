// Wave 0 RED scaffold — locks the (public) layout TopBar+Footer mount contract (Phase 1.6 REQ-1.6-4):
//  - (public)/layout.tsx mounts TopBar and Footer around children
//
// RED-by-design: PublicLayout does not yet import TopBar/Footer from features/landing.
// This spec becomes GREEN when Phase 1.6 Wave 2 lands.
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('@/features/landing/components/TopBar', () => ({
  default: () => <div data-testid="topbar" />,
}));
vi.mock('@/features/landing/components/Footer', () => ({
  default: () => <div data-testid="footer" />,
}));
vi.mock('next-intl/server', () => ({
  getTranslations: vi.fn(async () => (k: string) => k),
  getLocale: vi.fn(async () => 'en'),
}));
vi.mock('next/headers', () => ({
  headers: vi.fn(async () => ({ get: vi.fn(() => null) })),
  cookies: vi.fn(async () => ({ toString: (): string => '' })),
}));

import PublicLayout from '@/app/(public)/layout';

describe('(public)/layout.tsx', () => {
  it('mounts TopBar and Footer around children', async () => {
    const Layout = await PublicLayout({ children: <div data-testid="children" /> });
    render(Layout as React.ReactElement);
    expect(screen.getByTestId('topbar')).toBeInTheDocument();
    expect(screen.getByTestId('children')).toBeInTheDocument();
    expect(screen.getByTestId('footer')).toBeInTheDocument();
  });
});
