import { defineRouting } from 'next-intl/routing';

/**
 * next-intl routing configuration for Zero Mail (Phase 1.1).
 *
 * Locked decisions (CONTEXT.md):
 *  - D-B1: localePrefix = 'never' — keep unprefixed routes /login, /onboarding, /settings.
 *  - D-B3: defaultLocale = 'vi' — Vietnamese-first product (REQ-1).
 *  - D-B4: NEXT_LOCALE cookie is the unauthenticated persistence channel.
 *
 * Cookie hardening (RESEARCH.md Pitfall 4 — next-intl 4.x defaults the locale
 * cookie to session-only, which would silently break REQ-2 unauthenticated
 * persistence; the maxAge / sameSite / secure values below are mandated literals
 * grep'd by Plan 05 acceptance criteria and threat model T-1.1.05-02).
 */
export const routing = defineRouting({
  locales: ['vi', 'en'] as const,
  defaultLocale: 'vi',
  localePrefix: 'never',
  localeCookie: {
    name: 'NEXT_LOCALE',
    maxAge: 60 * 60 * 24 * 365, // 1 year (REQ-2 persistence)
    sameSite: 'lax',
    secure: true,
  },
});

export type AppLocale = (typeof routing.locales)[number];
