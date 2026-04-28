// Wave 0 RED scaffold — locks the /login RSC searchParams error rendering contract
// (Phase 01.5 D-B3, T-01.5-02-01 tamper-guard):
//  - No ?error → no role="alert" rendered
//  - ?error=consent_denied → Alert with auth.login.error.consent_denied.{title,body} text
//  - ?error=gmail_scope_required → Alert with auth.login.error.gmail_scope_required.{title,body} text
//  - ?error=javascript:alert(1) (tamper) → no role="alert" rendered (closed-enum guard)
//
// RED-by-design: LoginPage does not accept searchParams yet (Plan 02 Task 2 wires it).
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

// Mock next-intl/server before imports so the RSC module resolves
vi.mock('next-intl/server', () => ({
  getTranslations: vi.fn(async (namespace?: string) => {
    // Namespace-sensitive resolver. This catches real next-intl failures where
    // component lookup paths drift away from the message bundle shape.
    const allMessages: Record<string, string> = {
      'auth.error.consent_denied.title': 'Gmail access was declined',
      'auth.error.consent_denied.body':
        'Zero Mail needs Gmail access to triage your inbox. Click Sign in with Google to try again.',
      'auth.error.gmail_scope_required.title': 'Gmail permission missing',
      'auth.error.gmail_scope_required.body':
        "Sign in worked, but Gmail access wasn't granted. Click Sign in with Google and approve the Gmail permission to continue.",
      'auth.login.headline': 'Reach inbox zero without giving up control.',
      'auth.login.body':
        'Zero Mail connects to Gmail so you can set safe, reviewable automation rules.',
      'auth.login.googleButton': 'Sign in with Google',
      'auth.login.safety.noAutoSend': 'No auto-send',
      'auth.login.safety.noLongTermStorage': 'No long-term email body storage',
      'auth.login.safety.revokeAnytime': 'You can revoke access anytime',
    };
    return (key: string) => allMessages[namespace ? `${namespace}.${key}` : key] ?? key;
  }),
  getLocale: vi.fn(async () => 'en'),
}));

// Mock Next.js navigation primitives used inside LoginPage
vi.mock('next/navigation', () => ({
  useRouter: vi.fn(() => ({ push: vi.fn(), replace: vi.fn() })),
  useSearchParams: vi.fn(() => new URLSearchParams()),
}));

// Mock LanguageSwitcher — it imports next/navigation internally
vi.mock('@/i18n/components/LanguageSwitcher', () => ({
  LanguageSwitcher: () => null,
}));

vi.mock('@/features/auth/components/AuthTopBar', () => ({
  default: ({ children }: { children?: React.ReactNode }) => <header>{children}</header>,
}));

vi.mock('@/features/auth/components/TrustPanel', () => ({
  TrustPanel: () => <aside />,
}));

vi.mock('@/features/auth/components/LegalFooter', () => ({
  LegalFooter: () => <footer />,
}));

// Mock next/headers (not used in login page directly but may be transitively loaded)
vi.mock('next/headers', () => ({
  headers: vi.fn(async () => ({ get: vi.fn(() => null) })),
}));

import LoginPage from '@/app/(auth)/login/page';

/**
 * Render helper: LoginPage is async RSC; wrap the Promise in a React component.
 */
async function renderLoginPage(searchParams: Record<string, string>) {
  const Page = await LoginPage({ searchParams: Promise.resolve(searchParams) });
  return render(Page as React.ReactElement);
}

describe('/login RSC searchParams error rendering', () => {
  it('renders NO alert when searchParams has no error key', async () => {
    await renderLoginPage({});
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders destructive Alert with consent_denied i18n copy when ?error=consent_denied', async () => {
    await renderLoginPage({ error: 'consent_denied' });
    const alert = screen.getByRole('alert');
    expect(alert).toBeInTheDocument();
    expect(screen.getByText(/Gmail access was declined/i)).toBeInTheDocument();
    expect(screen.getByText(/Zero Mail needs Gmail access to triage/i)).toBeInTheDocument();
  });

  it('renders destructive Alert with gmail_scope_required i18n copy when ?error=gmail_scope_required', async () => {
    await renderLoginPage({ error: 'gmail_scope_required' });
    const alert = screen.getByRole('alert');
    expect(alert).toBeInTheDocument();
    expect(screen.getByText(/Gmail permission missing/i)).toBeInTheDocument();
    expect(screen.getByText(/Sign in worked, but Gmail access/i)).toBeInTheDocument();
  });

  it('renders NO alert for tampered ?error=javascript:alert(1) (closed-enum guard)', async () => {
    await renderLoginPage({ error: 'javascript:alert(1)' });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
