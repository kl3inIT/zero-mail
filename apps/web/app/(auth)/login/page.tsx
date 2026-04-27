import { getLocale, getTranslations } from 'next-intl/server';

import { LanguageSwitcher } from '@/i18n/components/LanguageSwitcher';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { buttonVariants } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import { getApiUrl } from '@/lib/api/base-url';

import type { AppLocale } from '@/i18n/routing';

/**
 * /login (RSC). Phase 01.5 Plan 02 — deflated from PageShell to raw <main>,
 * wired RSC searchParams for ?error=... rendering (D-B3).
 *
 * Closed-enum tamper-guard (T-01.5-02-01): only KNOWN_ERROR_CODES are allowed;
 * unknown values (including XSS attempts) render no Alert.
 *
 * Error codes produced by the backend (Plan 01): consent_denied (full deny)
 * and gmail_scope_required (partial grant). Both redirect here with ?error=.
 */

const KNOWN_ERROR_CODES = ['consent_denied', 'gmail_scope_required'] as const;
type LoginErrorCode = (typeof KNOWN_ERROR_CODES)[number];

function isKnownError(value: unknown): value is LoginErrorCode {
  return typeof value === 'string' && (KNOWN_ERROR_CODES as readonly string[]).includes(value);
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;
  const tLogin = await getTranslations('auth.login');
  // Cast error translator to loose shape to allow dynamic key construction for
  // KNOWN_ERROR_CODES. next-intl 4.x strict typed bundle cannot narrow template
  // literals; mirrors the LanguageSwitcher / StatusAlert LooseTranslator pattern.
  const tError = tLogin as unknown as (key: string) => string;
  const locale = (await getLocale()) as AppLocale;

  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <div className="flex w-full max-w-md flex-col gap-4">
        {isKnownError(error) && (
          <Alert variant="destructive">
            <AlertTitle>{tError(`error.${error}.title`)}</AlertTitle>
            <AlertDescription>{tError(`error.${error}.body`)}</AlertDescription>
          </Alert>
        )}
        <Card className="w-full p-8">
          <div className="flex items-start justify-between gap-3">
            <h1 className="text-3xl font-semibold tracking-tight">{tLogin('headline')}</h1>
            <LanguageSwitcher currentLocale={locale} authenticated={false} variant="compact" />
          </div>
          <p className="text-foreground mt-4 text-base">{tLogin('body')}</p>
          <ul className="text-muted-foreground mt-4 list-disc pl-5 text-sm">
            <li>{tLogin('safety.noAutoSend')}</li>
            <li>{tLogin('safety.noLongTermStorage')}</li>
            <li>{tLogin('safety.revokeAnytime')}</li>
          </ul>
          <a
            href={getApiUrl('/oauth2/authorization/google')}
            className={cn(buttonVariants(), 'mt-6 w-full')}
          >
            {tLogin('googleButton')}
          </a>
        </Card>
      </div>
    </main>
  );
}
