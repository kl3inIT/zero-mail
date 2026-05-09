// Locks the LanguageSwitcher behavior:
//  - optimistic NEXT_LOCALE cookie write before any network call
//  - PATCH /me/language only when authenticated; rolls back cookie on error
//  - router.refresh() invoked after success path (no full-page reload)
//  - aria-label sourced from settings.language.label key
//  - rollback flow shows the localized API error via useLocalizedApiError

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';

import viMessages from '@/i18n/messages/vi.json';
import enMessages from '@/i18n/messages/en.json';

const refreshSpy = vi.fn();
const patchSpy = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: refreshSpy, push: vi.fn(), replace: vi.fn() }),
}));

vi.mock('@/lib/api/client', async (importOriginal) => {
  const actual = (await importOriginal()) as Record<string, unknown>;
  return {
    ...actual,
    api: {
      PATCH: (...args: unknown[]) => patchSpy(...args),
      GET: vi.fn(),
      POST: vi.fn(),
      PUT: vi.fn(),
      DELETE: vi.fn(),
    },
    xsrfHeader: () => ({ 'X-XSRF-TOKEN': 'test-token' }),
  };
});

import { LanguageSwitcher } from '@/i18n/components/LanguageSwitcher';

function clearAllCookies() {
  if (typeof document === 'undefined') return;
  document.cookie.split(';').forEach((c) => {
    const eq = c.indexOf('=');
    const name = (eq > -1 ? c.slice(0, eq) : c).trim();
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  });
}

function renderWithIntl(node: React.ReactNode, locale: 'vi' | 'en' = 'vi') {
  const messages = locale === 'vi' ? viMessages : enMessages;
  return render(
    <NextIntlClientProvider locale={locale} messages={messages}>
      {node}
    </NextIntlClientProvider>,
  );
}

describe('LanguageSwitcher', () => {
  beforeEach(() => {
    clearAllCookies();
    refreshSpy.mockReset();
    patchSpy.mockReset();
  });

  afterEach(() => {
    clearAllCookies();
  });

  it('renders trigger with localized aria-label and current locale visible', () => {
    renderWithIntl(<LanguageSwitcher currentLocale="vi" authenticated={false} />);
    const trigger = screen.getByRole('button', { name: viMessages.settings.language.label });
    expect(trigger).toBeInTheDocument();
    // Current locale label visible (Tiếng Việt) in compact form
    expect(trigger.textContent ?? '').toContain('Tiếng Việt');
  });

  it('writes NEXT_LOCALE cookie immediately and triggers router.refresh when unauthenticated', async () => {
    renderWithIntl(<LanguageSwitcher currentLocale="vi" authenticated={false} />);
    fireEvent.click(screen.getByRole('button', { name: viMessages.settings.language.label }));
    // The "English" radio item should appear in the menu
    const enItem = await screen.findByRole('menuitemradio', { name: /English/i });
    fireEvent.click(enItem);

    await waitFor(() => {
      expect(document.cookie).toMatch(/NEXT_LOCALE=en/);
    });
    expect(patchSpy).not.toHaveBeenCalled();
    await waitFor(() => expect(refreshSpy).toHaveBeenCalled());
  });

  it('calls PATCH /me/language when authenticated and refreshes', async () => {
    patchSpy.mockResolvedValueOnce({ data: { preferredLanguage: 'en' }, error: undefined });
    renderWithIntl(<LanguageSwitcher currentLocale="vi" authenticated={true} />);
    fireEvent.click(screen.getByRole('button', { name: viMessages.settings.language.label }));
    const enItem = await screen.findByRole('menuitemradio', { name: /English/i });
    fireEvent.click(enItem);

    await waitFor(() => {
      expect(patchSpy).toHaveBeenCalledTimes(1);
    });
    const call = patchSpy.mock.calls[0];
    expect(call[0]).toBe('/me/language');
    expect(call[1].body).toEqual({ language: 'en' });
    expect(document.cookie).toMatch(/NEXT_LOCALE=en/);
    await waitFor(() => expect(refreshSpy).toHaveBeenCalled());
  });

  it('rolls back NEXT_LOCALE cookie when authenticated PATCH errors', async () => {
    patchSpy.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'error.validation.field.language.Pattern' },
      response: new Response(null, { status: 400 }),
    });
    renderWithIntl(<LanguageSwitcher currentLocale="vi" authenticated={true} />);
    fireEvent.click(screen.getByRole('button', { name: viMessages.settings.language.label }));
    const enItem = await screen.findByRole('menuitemradio', { name: /English/i });
    fireEvent.click(enItem);

    await waitFor(() => {
      expect(patchSpy).toHaveBeenCalled();
    });
    await waitFor(() => {
      // After error, cookie must be restored to vi
      expect(document.cookie).toMatch(/NEXT_LOCALE=vi/);
    });
    expect(document.cookie).not.toMatch(/NEXT_LOCALE=en/);
    // Localized validation field error is rendered (vi)
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        viMessages.errors.validation.field.language.Pattern,
      );
    });
  });

  it('writes cookie with required attributes (max-age + samesite=lax + secure)', async () => {
    // Capture document.cookie writes via a setter spy.
    const writes: string[] = [];
    const proto = Object.getPrototypeOf(document) as object;
    const desc = Object.getOwnPropertyDescriptor(Document.prototype, 'cookie');
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      get: () => desc?.get?.call(document) ?? '',
      set: (v: string) => {
        writes.push(v);
        desc?.set?.call(document, v);
      },
    });

    try {
      renderWithIntl(<LanguageSwitcher currentLocale="vi" authenticated={false} />);
      fireEvent.click(screen.getByRole('button', { name: viMessages.settings.language.label }));
      const enItem = await screen.findByRole('menuitemradio', { name: /English/i });
      fireEvent.click(enItem);

      await waitFor(() => {
        const localeWrite = writes.find((w) => w.startsWith('NEXT_LOCALE='));
        expect(localeWrite).toBeDefined();
        expect(localeWrite!.toLowerCase()).toMatch(/max-age=31536000/);
        expect(localeWrite!.toLowerCase()).toMatch(/samesite=lax/);
        expect(localeWrite!.toLowerCase()).toMatch(/secure/);
      });
    } finally {
      // Restore the original descriptor
      if (desc) Object.defineProperty(document, 'cookie', desc);
      void proto;
    }
  });

  // WR-03: network-level rejection (fetch throws because API is unreachable,
  // request is blocked, or JSON parsing fails). The optimistic cookie must be
  // rolled back and a localized fallback error rendered — without the
  // try/catch around api.PATCH, the cookie stayed changed and the user saw
  // no failure indication.
  it('rolls back NEXT_LOCALE cookie when authenticated PATCH rejects (network failure)', async () => {
    patchSpy.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    renderWithIntl(<LanguageSwitcher currentLocale="vi" authenticated={true} />);
    fireEvent.click(screen.getByRole('button', { name: viMessages.settings.language.label }));
    const enItem = await screen.findByRole('menuitemradio', { name: /English/i });
    fireEvent.click(enItem);

    await waitFor(() => {
      expect(patchSpy).toHaveBeenCalled();
    });
    await waitFor(() => {
      // After rejection, cookie must be restored to vi
      expect(document.cookie).toMatch(/NEXT_LOCALE=vi/);
    });
    expect(document.cookie).not.toMatch(/NEXT_LOCALE=en/);
    // Localized fallback error (errors.unknown) is rendered — never the raw
    // exception message.
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(viMessages.errors.unknown);
    });
    expect(screen.getByRole('alert').textContent ?? '').not.toMatch(/Failed to fetch/);
  });

  it('no-ops (no PATCH, no refresh) when selected locale equals current', async () => {
    renderWithIntl(<LanguageSwitcher currentLocale="vi" authenticated={true} />);
    fireEvent.click(screen.getByRole('button', { name: viMessages.settings.language.label }));
    const viItem = await screen.findByRole('menuitemradio', { name: /Tiếng Việt/i });
    fireEvent.click(viItem);

    // Allow any pending microtasks
    await new Promise((r) => setTimeout(r, 0));
    expect(patchSpy).not.toHaveBeenCalled();
    expect(refreshSpy).not.toHaveBeenCalled();
  });
});
