// Wave 0 RED scaffold — references implementation that lands in Plan 06.
// Locks the /onboarding mismatch alert contract (CONTEXT.md D-D1 + D-D6,
// UI-SPEC §Copywriting Contract, Plan 06 Task 1 step 6 handleRetry):
//  - StatusAlert variant=error rendered when ?error=gmail_identity_mismatch
//  - StatusAlert variant=error rendered when ?error=gmail_consent_denied
//  - NO alert for unknown ?error values (closed enum — Threat T-error-param-tamper)
//  - NO alert when ?error param absent
//  - Retry CTA fires BOTH side effects (Plan 06 contract):
//       1. window.location.href = '/tenant/connect-gmail'  (full-page nav to OAuth init)
//       2. router.replace('/onboarding')                   (URL hygiene)
//    Both assertions are required and non-collapsible.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';

const replaceMock = vi.fn();
const routerMock = { replace: replaceMock, push: vi.fn(), refresh: vi.fn() };

let searchParamsValue: URLSearchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => routerMock,
  useSearchParams: () => searchParamsValue,
  useParams: () => ({}),
  usePathname: () => '/onboarding',
}));

// Stub the data hooks so the page renders without a live API. The mismatch alert
// contract is purely about query-string handling + retry CTA; step-machine
// rendering happens BELOW the alert.
vi.mock('@/features/account/hooks/useCurrentUser', () => ({
  useCurrentUser: () => ({
    data: {
      id: 'fake-user-id',
      email: 'alice@example.com',
      onboardingStep: 'SIGNED_IN',
    },
  }),
}));
vi.mock('@/features/onboarding/hooks/useSelectTemplate', () => ({
  useSelectTemplate: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock('@/features/onboarding/hooks/useCompleteOnboarding', () => ({
  useCompleteOnboarding: () => ({ mutate: vi.fn(), isPending: false }),
}));

// RED-by-design: the onboarding page exists but does NOT yet read ?error or
// render StatusAlert (Plan 06 lands that wiring). Vitest will FAIL because the
// expected alert text / CTA is not in the DOM.
import OnboardingPage from '@/app/(protected)/onboarding/page';

const messages = {
  common: {
    app: { title: 'Zero Mail', description: 'Reach inbox zero.' },
    nav: { docs: 'Docs', signIn: 'Sign in' },
    loading: 'Loading...',
    loadingApp: 'Loading setup...',
    save: 'Save',
    cancel: 'Cancel',
    close: 'Close',
    retry: 'Try action again',
    savingLanguage: 'Updating...',
  },
  onboarding: {
    loading: 'Loading setup...',
    connect: { heading: 'Connect Gmail', body: 'Body', cta: 'Connect Gmail' },
    template: {
      heading: 'Choose template',
      body: 'Body',
      saveCta: 'Save',
    },
    completion: { heading: 'Done', cta: 'Continue' },
  },
  templates: {
    receipts: { title: 'Receipts', description: 'desc' },
    newsletters: { title: 'Newsletters', description: 'desc' },
    calendar: { title: 'Calendar', description: 'desc' },
  },
  gmail: {
    identity: {
      mismatch: {
        title: "Gmail account doesn't match",
        body: 'Please grant Gmail access using the same Google account you logged in with.',
        cta: 'Try again',
      },
    },
    consent: {
      denied: {
        title: 'Gmail access was declined',
        body: 'To use auto-triage, Zero Mail needs access to your inbox.',
        cta: 'Grant access',
      },
    },
  },
} as const;

function renderPage() {
  return render(
    <NextIntlClientProvider locale="en" messages={messages}>
      <OnboardingPage />
    </NextIntlClientProvider>,
  );
}

// Spy: each call records the assigned href value. Typed as a callable mock so
// TS narrows it correctly when invoked from the Proxy `set` trap.
let hrefSetter: ((value: string) => void) & { mock: { calls: unknown[][] } };
let originalLocation: Location;

beforeEach(() => {
  replaceMock.mockReset();
  searchParamsValue = new URLSearchParams();

  // Spy on every assignment to window.location.href without replacing the whole
  // location object (preserves jsdom's URL-parsing behavior elsewhere). This is
  // the most isolated way to assert on a navigation event.
  hrefSetter = vi.fn() as unknown as typeof hrefSetter;
  originalLocation = window.location;
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: new Proxy(originalLocation, {
      get: (target, prop) => {
        if (prop === 'href') return '';
        return Reflect.get(target, prop);
      },
      set: (_target, prop, value) => {
        if (prop === 'href') {
          hrefSetter(value as string);
          return true;
        }
        return Reflect.set(_target, prop, value);
      },
    }),
  });
});

afterEach(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: originalLocation,
  });
  vi.restoreAllMocks();
});

describe('/onboarding mismatch alert wiring', () => {
  it('renders StatusAlert variant=error when ?error=gmail_identity_mismatch', () => {
    searchParamsValue = new URLSearchParams('error=gmail_identity_mismatch');
    renderPage();
    expect(screen.getByText(/Gmail account doesn't match/i)).toBeInTheDocument();
  });

  it('renders StatusAlert variant=error when ?error=gmail_consent_denied', () => {
    searchParamsValue = new URLSearchParams('error=gmail_consent_denied');
    renderPage();
    expect(screen.getByText(/Gmail access was declined/i)).toBeInTheDocument();
  });

  it('does NOT render any alert when ?error is an unknown value (closed enum)', () => {
    // Threat T-error-param-tamper: arbitrary URL values must NOT render — the
    // page maps a closed set of tokens to i18n keys.
    searchParamsValue = new URLSearchParams('error=javascript:alert(1)');
    renderPage();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('does NOT render any alert when ?error param is absent', () => {
    searchParamsValue = new URLSearchParams();
    renderPage();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('clicking retry CTA fires BOTH side effects: navigate to /tenant/connect-gmail AND router.replace("/onboarding")', () => {
    searchParamsValue = new URLSearchParams('error=gmail_identity_mismatch');
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: /Try again/i }));

    // Assertion 1 (PRIMARY — what the user observes): full-page nav to OAuth init.
    expect(hrefSetter as unknown as ReturnType<typeof vi.fn>).toHaveBeenCalledWith(
      '/tenant/connect-gmail',
    );

    // Assertion 2 (SEPARATE — URL hygiene cleanup): query param cleared.
    expect(replaceMock).toHaveBeenCalledWith('/onboarding');

    // Both must fire — the test fails if either is missing (Plan 06 contract).
  });
});
