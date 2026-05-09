// Locks the landing page 4-section contract (Phase 1.6 REQ-1.6-3):
//  - (public)/page.tsx renders Hero, HowItWorks, Features, TrustPillars
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('@/features/landing/components/Hero', () => ({
  default: () => <div data-testid="hero" />,
}));
vi.mock('@/features/landing/components/HowItWorks', () => ({
  default: () => <div data-testid="how" />,
}));
vi.mock('@/features/landing/components/Features', () => ({
  default: () => <div data-testid="features" />,
}));
vi.mock('@/features/landing/components/TrustPillars', () => ({
  default: () => <div data-testid="trust" />,
}));

vi.mock('next-intl/server', () => ({ getTranslations: vi.fn(async () => (k: string) => k) }));
vi.mock('next/headers', () => ({
  headers: vi.fn(async () => ({ get: vi.fn(() => null) })),
  cookies: vi.fn(async () => ({ toString: (): string => '' })),
}));

import LandingPage from '@/app/(public)/page';

describe('(public)/page.tsx', () => {
  it('renders Hero, HowItWorks, Features, TrustPillars', async () => {
    const Page = await LandingPage();
    render(Page as React.ReactElement);
    expect(screen.getByTestId('hero')).toBeInTheDocument();
    expect(screen.getByTestId('how')).toBeInTheDocument();
    expect(screen.getByTestId('features')).toBeInTheDocument();
    expect(screen.getByTestId('trust')).toBeInTheDocument();
  });
});
