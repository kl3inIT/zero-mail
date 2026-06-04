/**
 * Canonical public site identity for SEO surfaces (metadataBase, sitemap,
 * robots, Organization JSON-LD). The production origin is zeromail.vn; override
 * with NEXT_PUBLIC_SITE_URL for previews/staging. Kept separate from the API
 * base (lib/api/base-url.ts) — that points at the backend, this points at the
 * public marketing/app origin a crawler sees.
 */
const DEFAULT_SITE_URL = 'https://zeromail.vn';

export const SITE_NAME = 'Zero Mail';

/** Absolute origin without a trailing slash, e.g. `https://zeromail.vn`. */
export function getSiteUrl(): string {
  const raw = process.env.NEXT_PUBLIC_SITE_URL?.trim();
  const base = raw && /^https?:\/\//i.test(raw) ? raw : DEFAULT_SITE_URL;
  return base.replace(/\/+$/, '');
}

/** Build an absolute public URL for `path` (must start with `/`). */
export function siteUrl(path: `/${string}`): string {
  return `${getSiteUrl()}${path}`;
}
