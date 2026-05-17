// Locks the landing page 3-section contract (Phase 1.6 REQ-1.6-3):
//  - (public)/page.tsx renders Hero, Features, Pricing, Testimonials, FAQ
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
vi.mock('@/features/landing/components/Pricing', () => ({
  default: () => <div data-testid="pricing" />,
}));
vi.mock('@/features/landing/components/Testimonials', () => ({
  default: () => <div data-testid="testimonials" />,
}));
vi.mock('@/features/landing/components/FAQ', () => ({
  default: () => <div data-testid="faq" />,
}));
vi.mock('@/features/landing/components/WaitlistDialog', () => ({
  default: () => <div data-testid="waitlist-dialog" />,
}));

vi.mock('next-intl/server', () => ({ getTranslations: vi.fn(async () => (k: string) => k) }));
vi.mock('next/headers', () => ({
  headers: vi.fn(async () => ({ get: vi.fn(() => null) })),
  cookies: vi.fn(async () => ({ toString: (): string => '' })),
}));

import LandingPage from '@/app/(public)/page';

describe('(public)/page.tsx', () => {
  it('renders Hero, Features, Pricing, Testimonials, FAQ, and waitlist dialog', async () => {
    const Page = await LandingPage();
    render(Page as React.ReactElement);
    expect(screen.getByTestId('hero')).toBeInTheDocument();
    expect(screen.getByTestId('features')).toBeInTheDocument();
    expect(screen.getByTestId('pricing')).toBeInTheDocument();
    expect(screen.getByTestId('testimonials')).toBeInTheDocument();
    expect(screen.getByTestId('faq')).toBeInTheDocument();
    expect(screen.getByTestId('waitlist-dialog')).toBeInTheDocument();
  });
});
