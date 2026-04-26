// Plan 06 Task 1 — flipped from RED stub to GREEN.
//
// Locks the FE error-localization display contract (CONTEXT.md decision D-D5
// + threat_model T-1.1.06-01/02):
//  - default locale vi -> Vietnamese localized string for known error codes
//  - locale switch to en -> English localized string for the same code
//  - unknown error code -> [MISSING:<code>] sentinel in dev, errors.unknown in prod
//  - never renders raw ProblemDetail title/detail, exception class names, SQL state,
//    or stack frame patterns

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import * as React from 'react';

import viMessages from '@/messages/vi.json';
import enMessages from '@/messages/en.json';
import { useLocalizedApiError, useLocalizedFieldError, type ApiError } from '@/lib/api/errors';

function Harness({ err }: { err: ApiError | undefined }) {
  const t = useLocalizedApiError();
  return <p data-testid="message">{t(err)}</p>;
}

function FieldHarness({ field, code }: { field: string; code: string }) {
  const t = useLocalizedFieldError();
  return <p data-testid="message">{t({ field, code } as never)}</p>;
}

function renderWithLocale(node: React.ReactNode, locale: 'vi' | 'en') {
  const messages = locale === 'vi' ? viMessages : enMessages;
  return render(
    <NextIntlClientProvider locale={locale} messages={messages}>
      {node}
    </NextIntlClientProvider>,
  );
}

describe('error code -> localized message rendering (Plan 06 GREEN)', () => {
  beforeEach(() => {
    // Default to a non-production env so the [MISSING:] sentinel surfaces.
    vi.stubEnv('NODE_ENV', 'development');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('renders Vietnamese for error.auth.unauthorized when locale=vi', () => {
    renderWithLocale(<Harness err={{ code: 'auth.unauthorized' } as ApiError} />, 'vi');
    expect(screen.getByTestId('message')).toHaveTextContent(viMessages.errors.auth.unauthorized);
  });

  it('renders English for error.auth.unauthorized when locale=en', () => {
    renderWithLocale(<Harness err={{ code: 'auth.unauthorized' } as ApiError} />, 'en');
    expect(screen.getByTestId('message')).toHaveTextContent(enMessages.errors.auth.unauthorized);
  });

  it('renders [MISSING:<code>] sentinel in dev for unknown code (NOT silent English fallback)', () => {
    vi.stubEnv('NODE_ENV', 'development');
    renderWithLocale(<Harness err={{ code: 'does.not.exist' } as ApiError} />, 'vi');
    expect(screen.getByTestId('message')).toHaveTextContent('[MISSING:does.not.exist]');
  });

  it('renders localized errors.unknown fallback in production for unknown code', () => {
    vi.stubEnv('NODE_ENV', 'production');
    renderWithLocale(<Harness err={{ code: 'totally.unknown.code' } as ApiError} />, 'vi');
    expect(screen.getByTestId('message')).toHaveTextContent(viMessages.errors.unknown);
    // Sentinel must NEVER leak into production output
    expect(screen.getByTestId('message').textContent ?? '').not.toMatch(/\[MISSING:/);
  });

  it('strips the legacy `error.` prefix from server codes when looking up bundle keys', () => {
    // Backend ErrorCodes constants are dotted with "error." prefix; the FE bundle
    // namespaces under "errors.*" so the hook strips that prefix.
    renderWithLocale(<Harness err={{ code: 'error.auth.unauthorized' } as ApiError} />, 'vi');
    expect(screen.getByTestId('message')).toHaveTextContent(viMessages.errors.auth.unauthorized);
  });

  it('uses errors.unknown when err is undefined', () => {
    renderWithLocale(<Harness err={undefined} />, 'vi');
    expect(screen.getByTestId('message')).toHaveTextContent(viMessages.errors.unknown);
  });

  it('never renders raw title / detail / exception names / SQL state / stack frame patterns', () => {
    // Even when the server-provided ApiError carries diagnostics, the hook must
    // never surface them — it switches on `code` only.
    const noisy = {
      code: 'auth.unauthorized',
      title: 'Internal Server Error',
      detail: 'org.hibernate.exception.ConstraintViolationException: ERROR: duplicate key value violates unique constraint "users_email_key"',
      // synthetic shape for completeness
      stack: 'at com.zeromail.core.account.AccountService.requireCurrentUser(AccountService.java:42)',
    } as unknown as ApiError;
    renderWithLocale(<Harness err={noisy} />, 'vi');
    const text = screen.getByTestId('message').textContent ?? '';
    expect(text).toBe(viMessages.errors.auth.unauthorized);
    expect(text).not.toMatch(/Internal Server Error/);
    expect(text).not.toMatch(/org\.hibernate/);
    expect(text).not.toMatch(/ConstraintViolationException/);
    expect(text).not.toMatch(/users_email_key/);
    expect(text).not.toMatch(/at com\.zeromail/);
    expect(text).not.toMatch(/\d{5}/); // SQL state shape (e.g. 23505)
  });

  it('renders localized field error when FieldError.code maps into errors.validation.field.*', () => {
    renderWithLocale(<FieldHarness field="language" code="validation.field.language.Pattern" />, 'vi');
    expect(screen.getByTestId('message')).toHaveTextContent(
      viMessages.errors.validation.field.language.Pattern,
    );
  });

  it('field error renders [MISSING:<code>] sentinel in dev for unknown field code', () => {
    vi.stubEnv('NODE_ENV', 'development');
    renderWithLocale(<FieldHarness field="x" code="not.a.real.code" />, 'vi');
    expect(screen.getByTestId('message')).toHaveTextContent('[MISSING:not.a.real.code]');
  });
});
