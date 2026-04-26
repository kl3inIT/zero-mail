import type { Metadata } from "next";
import { cookies, headers } from "next/headers";
import { Geist, Geist_Mono } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages, getTranslations } from "next-intl/server";

import { QueryProvider } from "@/lib/query-client";

import { routing } from "@/i18n/routing";

import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  // Vietnamese diacritics render via the Latin Extended block, which the
  // "latin" + "latin-ext" subsets cover (Geist does not expose a "vietnamese"
  // subset directly). Accessibility contract: see UI-SPEC §"Bilingual copy".
  subsets: ["latin", "latin-ext"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

/**
 * Localize <title> + <meta name="description"> via next-intl (UI-SPEC §"Routing
 * behavior" — `generateMetadata` localizes Phase 1 route title/description).
 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("common.app");
  return {
    title: t("title"),
    description: t("description"),
  };
}

/**
 * Server-side "source of truth" overwrite for NEXT_LOCALE (Plan 06 + CONTEXT.md
 * §Specifics + threat_model T-1.1.06-03):
 *  - When the user is authenticated AND `users.preferred_language` differs from
 *    the cookie, set the cookie to the server preference BEFORE rendering. This
 *    is what makes "device A → device B" sync work.
 *  - When unauthenticated, leave the cookie alone (the LanguageSwitcher's
 *    optimistic write is the only persistence).
 *
 * Implementation note: we proxy the incoming auth cookies to the API so the
 * /me call has the user's session. If the call fails (unauthenticated, server
 * down) we silently leave the cookie alone — never block layout rendering.
 * Privacy: we never log the response or correlate locale with email.
 */
async function reassertServerLocale(currentLocale: string): Promise<string> {
  const cookieStore = await cookies();
  const headerStore = await headers();
  const apiBase = process.env.NEXT_PUBLIC_API_BASE ?? "";
  if (!apiBase) return currentLocale;

  // Forward cookies so the API can identify the user.
  const cookieHeader = headerStore.get("cookie");
  if (!cookieHeader) return currentLocale;

  try {
    const res = await fetch(`${apiBase}/me`, {
      headers: { cookie: cookieHeader },
      cache: "no-store",
    });
    if (!res.ok) return currentLocale;
    const data = (await res.json()) as { preferredLanguage?: string };
    const preferred = data.preferredLanguage;
    if (
      preferred &&
      (preferred === "vi" || preferred === "en") &&
      preferred !== currentLocale
    ) {
      cookieStore.set("NEXT_LOCALE", preferred, {
        maxAge: 60 * 60 * 24 * 365,
        sameSite: "lax",
        secure: true,
        path: "/",
      });
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
  const cookieLocale = await getLocale();
  // Reassert from /me when authenticated; falls back to cookie when not.
  const locale = await reassertServerLocale(cookieLocale);
  // If the locale changed, refetch messages for the new locale; otherwise reuse.
  const messages =
    locale === cookieLocale
      ? await getMessages()
      : ((await import(`../messages/${locale}.json`)).default as Record<string, unknown>);

  // Defensive guard: ensure routing.locales contains the resolved locale before
  // rendering (prevents "<html lang=invalid>" if /me ever returns a bad value).
  const safeLocale: (typeof routing.locales)[number] =
    (routing.locales as readonly string[]).includes(locale)
      ? (locale as (typeof routing.locales)[number])
      : routing.defaultLocale;

  return (
    <html
      lang={safeLocale}
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <NextIntlClientProvider locale={safeLocale} messages={messages}>
          <QueryProvider>{children}</QueryProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
