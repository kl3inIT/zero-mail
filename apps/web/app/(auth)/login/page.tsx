import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button, buttonVariants } from '@/components/ui/button';
import { LegalFooter } from '@/features/auth/components/LegalFooter';
import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { GoogleG, MailIcon } from '@/features/landing/components/PrototypeIcons';
import { getPublicApiUrl } from '@/lib/api/base-url';
import { cn } from '@/lib/utils';

/**
 * /login (RSC). Phase 01.5 Plan 02 — deflated from PageShell to raw <main>,
 * wired RSC searchParams for ?error=... rendering (D-B3).
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

const KNOWN_ERROR_CODES = [
  'consent_denied',
  'gmail_scope_required',
  'mailbox_already_connected',
  'mailbox_in_other_workspace',
  'signin_failed',
] as const;
type LoginErrorCode = (typeof KNOWN_ERROR_CODES)[number];

function isKnownError(value: unknown): value is LoginErrorCode {
  return typeof value === 'string' && (KNOWN_ERROR_CODES as readonly string[]).includes(value);
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const [{ error }, t, tLogin, tAuthError] = await Promise.all([
    searchParams,
    getTranslations(),
    getTranslations('auth.login'),
    getTranslations('auth.error'),
  ]);
  // Cast error translator to loose shape to allow dynamic key construction for
  // KNOWN_ERROR_CODES. next-intl 4.x strict typed bundle cannot narrow template
  // literals; mirrors the LanguageSwitcher / StatusAlert LooseTranslator pattern.
  const tError = tAuthError as unknown as (key: string) => string;
  return (
    <div className="zm-auth flex min-h-screen flex-col">
      <AuthTopBar />
      <main className="zm-auth-main flex-1">
        <div className="zm-auth-grid zm-login-grid">
          <div className="zm-auth-panel zm-login-panel">
            <div className="flex flex-col gap-1">
              <h1 className="zm-auth-title">
                <span>{tLogin('headlineA')}</span>
                <em>{tLogin('headlineB')}</em>
              </h1>
              <p className="zm-auth-sub">{tLogin('body')}</p>
            </div>
            <div className="zm-signin-card">
              {isKnownError(error) && (
                <Alert variant="destructive">
                  <AlertTitle>{tError(`${error}.title`)}</AlertTitle>
                  <AlertDescription>{tError(`${error}.body`)}</AlertDescription>
                </Alert>
              )}
              <a
                href={getPublicApiUrl('/oauth2/authorization/google')}
                className={cn(buttonVariants({ variant: 'outline', size: 'lg' }), 'zm-google-btn')}
              >
                <GoogleG size={18} />
                {tLogin('googleButton')}
              </a>
              <p className="zm-signin-helper">
                <span>{tLogin('betaNote')}</span>
                <span>{tLogin('noCard')}</span>
              </p>
              <Button type="button" variant="outline" className="zm-work-gmail-btn" disabled>
                <MailIcon size={14} />
                {tLogin('workEmail')}
              </Button>
              <LegalFooter className="zm-signin-terms text-left" />
            </div>
          </div>
        </div>
      </main>
      <footer className="zm-auth-footer">
        <span>{t('footer.copyright')}</span>
        <span className="inline-flex items-center gap-2">
          <Link href="/privacy">{t('footer.privacy')}</Link>
          <span>·</span>
          <Link href="/terms">{t('footer.terms')}</Link>
          <span>·</span>
          <Link href="/docs">{t('auth.help')}</Link>
        </span>
      </footer>
    </div>
  );
}
