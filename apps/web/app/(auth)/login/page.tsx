import { getLocale, getTranslations } from 'next-intl/server';

import { LanguageSwitcher } from '@/i18n/components/LanguageSwitcher';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import { getApiUrl } from '@/lib/api/base-url';

import type { AppLocale } from '@/i18n/routing';

/**
 * /login (RSC). Phase 01.5 Plan 02 — deflated from PageShell to raw <main>,
 * wired RSC searchParams for ?error=... rendering (D-B3).
 * Phase 01.5 Plan 04 — visual polish via frontend-design skill (D-D1).
 *
 * Design intent (Plan 04):
 *  - Card with explicit header/content separation: heading large + confident,
 *    safety microcopy as small supplementary beneath button.
 *  - Error Alert rendered above Card with gap-6 separation — visible but
 *    not dominating; destructive variant reads as a status indicator, not alarm.
 *  - LanguageSwitcher anchored top-right within card header row.
 *  - Google button full-width, size=lg — clear primary action.
 *  - Body text is muted-foreground to keep heading primary.
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
      <div className="flex w-full max-w-md flex-col gap-6">
        {isKnownError(error) && (
          <Alert variant="destructive">
            <AlertTitle>{tError(`error.${error}.title`)}</AlertTitle>
            <AlertDescription>{tError(`error.${error}.body`)}</AlertDescription>
          </Alert>
        )}
        <Card className="w-full">
          <CardHeader className="pb-0">
            <div className="flex items-start justify-between gap-3">
              <h1 className="text-foreground text-2xl leading-snug font-bold tracking-tight">
                {tLogin('headline')}
              </h1>
              <LanguageSwitcher currentLocale={locale} authenticated={false} variant="compact" />
            </div>
          </CardHeader>
          <CardContent className="flex flex-col gap-6 pt-4">
            <p className="text-muted-foreground text-sm leading-relaxed">{tLogin('body')}</p>
            <a
              href={getApiUrl('/oauth2/authorization/google')}
              className={cn(buttonVariants({ size: 'lg' }), 'w-full')}
            >
              {tLogin('googleButton')}
            </a>
            <ul className="text-muted-foreground space-y-1.5 text-xs">
              <li className="flex items-start gap-2">
                <span className="mt-0.5 shrink-0 opacity-50">&#x2713;</span>
                {tLogin('safety.noAutoSend')}
              </li>
              <li className="flex items-start gap-2">
                <span className="mt-0.5 shrink-0 opacity-50">&#x2713;</span>
                {tLogin('safety.noLongTermStorage')}
              </li>
              <li className="flex items-start gap-2">
                <span className="mt-0.5 shrink-0 opacity-50">&#x2713;</span>
                {tLogin('safety.revokeAnytime')}
              </li>
            </ul>
          </CardContent>
        </Card>
      </div>
    </main>
  );
}
