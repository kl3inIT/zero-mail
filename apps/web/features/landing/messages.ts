/**
 * SEO copy for the public landing page (homepage).
 *
 * These keys back the homepage `generateMetadata` in `app/(public)/page.tsx`.
 * The bare brand string `common.app.title` ("Zero Mail") stays the site name /
 * title template; the landing page overrides it with a value-proposition title
 * + a 140–155 char meta description so the SERP snippet carries real intent
 * keywords (AI, Gmail, inbox zero) instead of just the brand name.
 */
export const landingSeoMessages = {
  'landing.seo.title': {
    vi: 'Zero Mail — AI tự động dọn Gmail, đạt inbox zero',
    en: 'Zero Mail — AI inbox cleanup for Gmail, reach inbox zero',
  },
  'landing.seo.description': {
    vi: 'Zero Mail dùng AI tự động đọc, phân loại và soạn sẵn trả lời Gmail theo quy tắc tiếng Việt bạn viết. Đạt inbox zero mà vẫn giữ toàn quyền kiểm soát.',
    en: 'Zero Mail uses AI to auto-triage, label, archive and draft replies in Gmail from rules you write in plain language. Reach inbox zero while staying in control.',
  },
} as const;

/**
 * Legal links surfaced in the public landing header (`TopBar`). The terms page is
 * labelled "Chính sách" and the privacy page "Bảo mật" per product wording. Kept
 * separate from `nav.privacy` (owned by `shell/messages.ts`, different copy) so the
 * header has its own dedicated labels.
 */
export const landingNavMessages = {
  'nav.terms': {
    vi: 'Chính sách',
    en: 'Terms',
  },
  'nav.privacyPolicy': {
    vi: 'Bảo mật',
    en: 'Privacy',
  },
} as const;
