// Locks the landing page section contract:
//  - (public)/page.tsx renders Hero, ReferralSection, Features, About, Partnership,
//    Testimonials, FAQ, Contact
//  - Homepage leads with Hero then the event referral band; Pricing intentionally not rendered.
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
vi.mock('@/features/landing/components/Testimonials', () => ({
  default: () => <div data-testid="testimonials" />,
}));
vi.mock('@/features/landing/components/FAQ', () => ({
  default: () => <div data-testid="faq" />,
}));
vi.mock('@/features/landing/components/Contact', () => ({
  default: () => <div data-testid="contact" />,
}));
// LandingReferralSection is an async server component that fetches the public
// referral endpoint. Mock it (and the other two new homepage sections) so the
// jsdom test does not try to run the server-component fetch.
vi.mock('@/features/referrals/components/LandingReferralSection', () => ({
  LandingReferralSection: () => <div data-testid="referral" />,
}));
vi.mock('@/features/landing/components/LandingAbout', () => ({
  LandingAbout: () => <div data-testid="about" />,
}));
vi.mock('@/features/landing/components/LandingPartnership', () => ({
  LandingPartnership: () => <div data-testid="partnership" />,
}));

vi.mock('next-intl/server', () => ({ getTranslations: vi.fn(async () => (k: string) => k) }));
vi.mock('next/headers', () => ({
  headers: vi.fn(async () => ({ get: vi.fn(() => null) })),
  cookies: vi.fn(async () => ({ toString: (): string => '' })),
}));

import LandingPage from '@/app/(public)/page';

describe('(public)/page.tsx', () => {
  it('renders Hero, Referral, Features, About, Partnership, Testimonials, FAQ, and Contact', async () => {
    const Page = await LandingPage();
    render(Page as React.ReactElement);
    expect(screen.getByTestId('hero')).toBeInTheDocument();
    expect(screen.getByTestId('referral')).toBeInTheDocument();
    expect(screen.getByTestId('features')).toBeInTheDocument();
    expect(screen.getByTestId('about')).toBeInTheDocument();
    expect(screen.getByTestId('partnership')).toBeInTheDocument();
    expect(screen.getByTestId('testimonials')).toBeInTheDocument();
    expect(screen.getByTestId('faq')).toBeInTheDocument();
    expect(screen.getByTestId('contact')).toBeInTheDocument();
    expect(screen.queryByTestId('how')).not.toBeInTheDocument();
    expect(screen.queryByTestId('pricing')).not.toBeInTheDocument();
  });
});
