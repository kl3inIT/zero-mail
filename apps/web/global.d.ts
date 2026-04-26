import type { routing } from './i18n/routing';
import type messages from './i18n/messages/en.json';

/**
 * Augments next-intl's AppConfig so `useTranslations`, `getTranslations`, and
 * `t(key)` calls are typed against the actual messages bundle (CONTEXT.md
 * D-A3). Plan 07's CI parity gate will surface any drift between vi.json and
 * en.json before this typing can lie at runtime.
 */
declare module 'next-intl' {
  interface AppConfig {
    Locale: (typeof routing.locales)[number];
    Messages: typeof messages;
  }
}
