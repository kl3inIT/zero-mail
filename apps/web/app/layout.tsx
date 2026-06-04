import type { Metadata } from 'next';
import { cookies, headers } from 'next/headers';
import { getLocale, getTranslations } from 'next-intl/server';

import { getCurrentUserCached } from '@/features/account/api/account-api';
import { routing } from '@/i18n/routing';
import { getApiBase } from '@/lib/api/base-url';
import { getSiteUrl, SITE_NAME, siteUrl } from '@/lib/site';

import './globals.css';

/** Brand mark Google reads for the search-result logo (white-bg square, ≥112px). */
const BRAND_LOGO_URL = siteUrl('/icon.png');

/**
 * Localize <title> + <meta name="description"> via next-intl (UI-SPEC §"Routing
 * behavior" — `generateMetadata` localizes Phase 1 route title/description) and
 * publish the site-wide SEO surface: `metadataBase` (so relative OG/canonical
 * URLs resolve), an Open Graph / Twitter card, and a title template so child
 * routes render "<Page> · Zero Mail". Favicon links are emitted automatically by
 * the app/icon.png, app/apple-icon.png and app/favicon.ico file conventions.
 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('common.app');
  const title = t('title');
  const description = t('description');
  return {
    metadataBase: new URL(getSiteUrl()),
    applicationName: SITE_NAME,
    title: { default: title, template: `%s · ${SITE_NAME}` },
    description,
    openGraph: {
      type: 'website',
      siteName: SITE_NAME,
      title,
      description,
      locale: 'vi_VN',
      alternateLocale: ['en_US'],
      images: [{ url: BRAND_LOGO_URL, width: 512, height: 512, alt: SITE_NAME }],
    },
    twitter: {
      card: 'summary',
      title,
      description,
      images: [BRAND_LOGO_URL],
    },
  };
}

/**
 * Server-side "source of truth" READ for NEXT_LOCALE (Plan 06 + CONTEXT.md
 * §Specifics + threat_model T-1.1.06-03):
 *  - When the user is authenticated AND `users.preferred_language` differs from
 *    the cookie, return the server preference so this render uses it. The
 *    persistent cookie write itself happens in `proxy.ts` (a response-capable
 *    boundary) — Server Components cannot reliably mutate response cookies, and
 *    silent failures there were producing a stale cookie + correct render
 *    split that broke "device A -> device B" sync. (WR-02.)
 *  - When unauthenticated, leave the cookie alone (the LanguageSwitcher's
 *    optimistic write is the only persistence).
 *
 * Implementation note: we proxy the incoming auth cookies to the API so the
 * /me call has the user's session. If the call fails (unauthenticated, server
 * down) we silently fall back to the incoming cookie — never block layout
 * rendering. Privacy: we never log the response or correlate locale with email.
 */
async function reassertServerLocale(currentLocale: string): Promise<string> {
  const headerStore = await headers();
  const apiBase = getApiBase();
  if (!apiBase) return currentLocale;

  // Forward cookies so the API can identify the user.
  // Use cookies().toString() as a primitive cache key so getCurrentUserCached
  // can dedupe across multiple RSC consumers in one render. headers().get('cookie')
  // is used only for the early-exit guard; the actual cache key is the full
  // cookie store serialized to a stable primitive string.
  if (!headerStore.get('cookie')) return currentLocale;

  try {
    // Isomorphic /me consolidation — same function backs proxy.ts (write side)
    // and this layout (read side). RSC fetch does NOT auto-forward Cookie; we
    // pass it via the cached wrapper. Pass the cookie header as a PRIMITIVE
    // STRING (not a fresh { headers: { cookie } } object) so React cache() can
    // dedupe by value equality across multiple calls within this render pass.
    const cookieHeader = (await cookies()).toString();
    const user = await getCurrentUserCached(cookieHeader);
    const preferred = user.preferredLanguage;
    if (preferred === 'vi' || preferred === 'en') {
      // Read-only: the cookie write is owned by proxy.ts where response
      // headers are reliably mutable.
      return preferred;
    }
  } catch {
    // Silent — never block render on a /me failure.
  }
  return currentLocale;
}

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const cookieStore = await cookies();
  const themeValue = cookieStore.get('zm-theme')?.value;
  const theme: 'light' | 'dark' = themeValue === 'dark' ? 'dark' : 'light';

  // Organization + WebSite structured data — drives the logo and site name
  // Google can surface in search results / the knowledge panel (distinct from
  // the favicon, which is the file-convention icon above).
  const site = getSiteUrl();
  const appTranslations = await getTranslations('common.app');
  const structuredData = {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'Organization',
        '@id': `${site}/#organization`,
        name: SITE_NAME,
        url: site,
        logo: {
          '@type': 'ImageObject',
          url: BRAND_LOGO_URL,
          width: 512,
          height: 512,
        },
        description: appTranslations('description'),
        sameAs: ['https://github.com/kl3inIT/zero-mail'],
      },
      {
        '@type': 'WebSite',
        '@id': `${site}/#website`,
        name: SITE_NAME,
        url: site,
        publisher: { '@id': `${site}/#organization` },
        inLanguage: ['vi', 'en'],
      },
    ],
  };
  const cookieLocale = await getLocale();
  // Reassert from /me when authenticated; falls back to cookie when not.
  const locale = await reassertServerLocale(cookieLocale);
  // Defensive guard: ensure routing.locales contains the resolved locale before
  // rendering (prevents "<html lang=invalid>" if /me ever returns a bad value).
  const safeLocale: (typeof routing.locales)[number] = (
    routing.locales as readonly string[]
  ).includes(locale)
    ? (locale as (typeof routing.locales)[number])
    : routing.defaultLocale;

  return (
    <html lang={safeLocale} className={`${theme === 'dark' ? 'dark' : ''} h-full antialiased`}>
      <body className="flex min-h-full flex-col">
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData) }}
        />
        {children}
      </body>
    </html>
  );
}
