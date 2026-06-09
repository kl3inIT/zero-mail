/**
 * SEO + intro copy for the standalone public marketing pages (/features,
 * /pricing, /about). Merged into i18n/messages/{vi,en}.json by
 * scripts/merge-feature-i18n.ts. Keeping these as distinct top-level routes
 * (each with its own canonical) gives Google indexable pages that can surface as
 * brand sitelinks.
 */
export const marketingMessages = {
  // ── Features page ──────────────────────────────────────────────
  'features.seo.title': {
    vi: 'Tính năng — Zero Mail | AI tự động hoá Gmail',
    en: 'Features — Zero Mail | AI Gmail automation',
  },
  'features.seo.description': {
    vi: 'Tất cả tính năng của Zero Mail: tự động phân loại, gắn nhãn, lưu trữ, soạn nháp trả lời và huỷ đăng ký — theo quy tắc bạn viết bằng tiếng Việt.',
    en: 'Everything Zero Mail does: auto-triage, label, archive, draft replies and bulk unsubscribe — from rules you write in plain language.',
  },
  'features.heading': {
    vi: 'Tính năng',
    en: 'Features',
  },
  'features.intro': {
    vi: 'Mọi thứ Zero Mail làm để giúp bạn đạt inbox zero — tự động, theo cách bạn muốn, và luôn trong tầm kiểm soát của bạn.',
    en: 'Everything Zero Mail does to help you reach inbox zero — automatically, your way, and always under your control.',
  },

  // ── About page ─────────────────────────────────────────────────
  'about.seo.title': {
    vi: 'Về chúng tôi — Zero Mail',
    en: 'About — Zero Mail',
  },
  'about.seo.description': {
    vi: 'Zero Mail là trợ lý email AI cho Gmail, ưu tiên tiếng Việt — giúp bạn đạt inbox zero mà vẫn giữ toàn quyền kiểm soát và quyền riêng tư.',
    en: 'Zero Mail is a Vietnamese-first AI email assistant for Gmail — helping you reach inbox zero while staying in full control of your privacy.',
  },
  'about.heading': {
    vi: 'Về Zero Mail',
    en: 'About Zero Mail',
  },
  'about.lead': {
    vi: 'Zero Mail là một trợ lý email AI cho Gmail, được xây dựng ưu tiên cho người dùng Việt. Mục tiêu của chúng tôi đơn giản: giúp bạn đạt inbox zero mà không phải đánh đổi quyền kiểm soát hay quyền riêng tư.',
    en: 'Zero Mail is an AI email assistant for Gmail, built Vietnamese-first. Our goal is simple: help you reach inbox zero without giving up control or privacy.',
  },
  'about.mission.title': {
    vi: 'Chúng tôi làm gì',
    en: 'What we do',
  },
  'about.mission.body': {
    vi: 'Zero Mail tự động đọc, phân loại, gắn nhãn, lưu trữ và soạn sẵn câu trả lời cho email — theo các quy tắc bạn viết bằng tiếng Việt tự nhiên. Thay vì học cú pháp bộ lọc, bạn chỉ cần mô tả ý muốn của mình, và AI hiểu ngữ cảnh để thực thi.',
    en: 'Zero Mail automatically reads, categorizes, labels, archives and pre-drafts replies — from rules you write in plain language. Instead of learning filter syntax, you describe what you want, and the AI understands the context to act on it.',
  },
  'about.privacy.title': {
    vi: 'Quyền riêng tư là nền tảng',
    en: 'Privacy by design',
  },
  'about.privacy.body': {
    vi: 'Chúng tôi không lưu trữ dài hạn nội dung email của bạn. Các cam kết bảo mật không chỉ là lời hứa trên giấy — chúng được thực thi bằng chính kiến trúc của hệ thống. Bạn luôn là người xem lại và quyết định trước những hành động quan trọng như gửi thư.',
    en: 'We do not store your email content long-term. Our privacy commitments are not just policy promises — they are enforced by the system architecture itself. You always review and decide before important actions like sending mail.',
  },
  'about.status.title': {
    vi: 'Trạng thái dự án',
    en: 'Project status',
  },
  'about.status.body': {
    vi: 'Zero Mail hiện là một dự án học thuật ở giai đoạn beta, chưa ra mắt thương mại đầy đủ. Chúng tôi minh bạch về điều này vì muốn bạn ra quyết định có hiểu biết trước khi kết nối hộp thư thật. Tính năng, giá và mức độ sẵn có có thể thay đổi khi chúng tôi tiếp tục hoàn thiện.',
    en: 'Zero Mail is currently a beta-stage academic project, not yet a fully launched commercial product. We are transparent about this because we want you to make an informed decision before connecting your real inbox. Features, pricing and availability may change as we keep improving.',
  },
  'about.cta.heading': {
    vi: 'Sẵn sàng đạt inbox zero?',
    en: 'Ready to reach inbox zero?',
  },
  'about.cta.body': {
    vi: 'Kết nối Gmail và để AI lo phần email lặp lại — miễn phí trong giai đoạn beta.',
    en: 'Connect Gmail and let AI handle the repetitive email — free during the beta.',
  },
  'about.cta.button': {
    vi: 'Bắt đầu miễn phí',
    en: 'Get started free',
  },

  // ── Nav / footer labels for the new pages ──────────────────────
  'nav.about': {
    vi: 'Về chúng tôi',
    en: 'About',
  },
} as const;
