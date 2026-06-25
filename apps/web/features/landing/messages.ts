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
 * Strategic-partner spotlight on the landing page (`LandingPartnership`). Factual copy only — no
 * fabricated quotes and no DTH credential showcase (this is Zero Mail's landing, not a DTH ad). The
 * copy describes the partnership itself; DTH is identified by a neutral one-line descriptor.
 */
export const landingPartnershipMessages = {
  'landingPartnership.heading': {
    vi: 'Đồng hành cùng DTH Software',
    en: 'Partnering with DTH Software',
  },
  'landingPartnership.subtitle': {
    vi: 'Tháng 6/2026, Zero Mail hợp tác chiến lược cùng DTH Software — đưa trợ lý email AI vào quy trình làm việc thực tế của một doanh nghiệp công nghệ Việt.',
    en: 'In June 2026, Zero Mail entered a strategic partnership with DTH Software — bringing the AI email assistant into a real Vietnamese tech company workflow.',
  },
  'landingPartnership.brandLabel': {
    vi: 'Giải pháp chuyển đổi số · Hà Nội',
    en: 'Digital transformation · Hanoi',
  },
  'landingPartnership.body': {
    vi: 'DTH Software chọn Zero Mail làm trợ lý email cho toàn đội ngũ — tự động phân loại, soạn nháp và dọn hộp thư Gmail. Hai bên đồng hành đưa AI vào công việc thực tế và cùng xây dựng hệ sinh thái sản phẩm Việt.',
    en: 'DTH Software picked Zero Mail as the email assistant for its whole team — auto-triaging, drafting, and cleaning up Gmail. Together we bring AI into real work and build a Vietnamese product ecosystem.',
  },
  'landingPartnership.point1': {
    vi: 'Toàn đội ngũ DTH dùng Zero Mail',
    en: "DTH's whole team on Zero Mail",
  },
  'landingPartnership.point2': {
    vi: 'Đồng hành truyền thông & sự kiện',
    en: 'Joint communications & events',
  },
  'landingPartnership.point3': {
    vi: 'Cùng xây hệ sinh thái AI Make in Vietnam',
    en: 'Building a Make-in-Vietnam AI ecosystem',
  },
} as const;

/**
 * "About us" section on the landing page (`LandingAbout`) — a compact, inspirational version of the
 * full `/about` page. Story + Vision / Mission / Core values, aligned with the existing about-page
 * tone (privacy-first, Vietnamese-first, honest about the beta/academic origin).
 */
export const landingAboutMessages = {
  'landingAbout.heading': {
    vi: 'Về chúng tôi',
    en: 'About us',
  },
  'landingAbout.story': {
    vi: 'Zero Mail bắt đầu từ một câu hỏi: tại sao AI thông minh đến vậy mà inbox vẫn ngập hàng trăm email chưa đọc? Là sinh viên FPT, chúng tôi xây Zero Mail như dự án EXE202 — với tham vọng biến nó thành sản phẩm SaaS người Việt tin dùng.',
    en: 'Zero Mail started from a question: why is AI this smart, yet our inbox still drowns in hundreds of unread emails? As FPT students, we built Zero Mail as an EXE202 project — with the ambition to grow it into a SaaS that Vietnamese users trust.',
  },
  'landingAbout.visionTitle': {
    vi: 'Tầm nhìn',
    en: 'Vision',
  },
  'landingAbout.visionBody': {
    vi: 'Trở thành trợ lý email AI mặc định cho người Việt — nơi ai làm việc với Gmail cũng đạt inbox zero mà không phải lo về quyền riêng tư.',
    en: 'To become the default AI email assistant for Vietnamese users — where anyone working in Gmail reaches inbox zero without worrying about privacy.',
  },
  'landingAbout.missionTitle': {
    vi: 'Sứ mệnh',
    en: 'Mission',
  },
  'landingAbout.missionBody': {
    vi: 'Giúp người bận rộn đạt inbox zero bằng AI tự động phân loại, soạn nháp và dọn hộp thư — theo quy tắc tiếng Việt, với toàn quyền kiểm soát.',
    en: 'Help busy people reach inbox zero with AI that auto-triages, drafts, and cleans up the inbox — by rules written in plain Vietnamese, with full control.',
  },
  'landingAbout.valuesTitle': {
    vi: 'Giá trị cốt lõi',
    en: 'Core values',
  },
  'landingAbout.value1': {
    vi: 'Riêng tư trên hết — email là của bạn',
    en: 'Privacy first — your email is yours',
  },
  'landingAbout.value2': {
    vi: 'Tiếng Việt-first, không phải tính năng phụ',
    en: 'Vietnamese-first, not an add-on',
  },
  'landingAbout.value3': {
    vi: 'Tin cậy — không tự ý hành động thay bạn',
    en: 'Trustworthy — never acts on your behalf without you',
  },
  'landingAbout.value4': {
    vi: 'Minh bạch — bạn luôn xem lại được AI đã làm gì',
    en: 'Transparent — you can always review what the AI did',
  },
  'landingAbout.moreCta': {
    vi: 'Tìm hiểu thêm về Zero Mail',
    en: 'Learn more about Zero Mail',
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
