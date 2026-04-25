import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages, getTranslations } from "next-intl/server";

import { QueryProvider } from "@/lib/query-client";

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

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const locale = await getLocale();
  const messages = await getMessages();

  return (
    <html
      lang={locale}
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <NextIntlClientProvider locale={locale} messages={messages}>
          <QueryProvider>{children}</QueryProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
