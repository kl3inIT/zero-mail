export const aiMessages = {
  'ai.page.title': {
    vi: 'Cấu hình AI',
    en: 'AI configuration',
  },
  'ai.page.description': {
    vi: 'Dạy AI cách hành xử với hộp thư của bạn — ai được bảo vệ khỏi tự động hoá, và (sắp tới) giọng văn cùng phong cách bạn muốn AI bắt chước khi soạn nháp.',
    en: 'Teach the AI how to behave in your inbox — who is shielded from automation, and (soon) the voice and style you want it to mirror when drafting replies.',
  },
  'ai.senders.heading': {
    vi: 'Người gửi được bảo vệ',
    en: 'Protected senders',
  },
  'ai.senders.description': {
    vi: 'Email từ những người gửi này luôn được giữ nguyên — không gắn nhãn, không lưu trữ, không soạn nháp tự động, dù quy tắc có khớp đến đâu.',
    en: 'Mail from these senders is always left alone — no labels, no archive, no auto-drafts, no matter how strongly a rule matches.',
  },
  'ai.senders.inputLabel': {
    vi: 'Email người gửi cần bảo vệ',
    en: 'Sender email to protect',
  },
  'ai.senders.inputPlaceholder': {
    vi: 'sep@congty.com',
    en: 'boss@company.com',
  },
  'ai.senders.add': {
    vi: 'Bảo vệ',
    en: 'Protect',
  },
  'ai.senders.adding': {
    vi: 'Đang thêm…',
    en: 'Adding…',
  },
  'ai.senders.added': {
    vi: 'Đã bảo vệ {email}',
    en: 'Protected {email}',
  },
  'ai.senders.addFailed': {
    vi: 'Chưa thêm được người gửi. Hãy thử lại.',
    en: 'Could not protect this sender. Try again.',
  },
  'ai.senders.invalidEmail': {
    vi: 'Email không hợp lệ.',
    en: 'Invalid email address.',
  },
  'ai.writingProfile.title': {
    vi: 'Phong cách AI viết',
    en: 'AI writing profile',
  },
  'ai.writingProfile.description': {
    vi: 'Hồ sơ này được áp dụng cho trợ lý chat lẫn nút Tạo bằng AI trong hộp thư. Bỏ trống nếu chưa muốn dùng.',
    en: 'Applied to both the chat assistant and the Generate button in the inbox composer. Leave blank to skip.',
  },
  'ai.writingProfile.personalInstructions.label': {
    vi: 'Hướng dẫn cá nhân',
    en: 'Personal instructions',
  },
  'ai.writingProfile.personalInstructions.placeholder': {
    vi: 'Ví dụ: Tôi tên Nguyễn Văn A, Founder Zero Mail. Luôn ký "Trân trọng, A". Tôi đang tuyển intern Q1 2026.',
    en: 'Example: My name is Alex, founder of Zero Mail. Always sign off "Best, Alex". I am hiring interns in Q1 2026.',
  },
  'ai.writingProfile.personalInstructions.help': {
    vi: 'Bối cảnh AI cần biết về bạn — tên, vai trò, dự án hiện tại, chữ ký mong muốn.',
    en: 'Context the AI should know about you — name, role, current projects, preferred sign-off.',
  },
  'ai.writingProfile.writingStyle.label': {
    vi: 'Phong cách viết',
    en: 'Writing style',
  },
  'ai.writingProfile.writingStyle.placeholder': {
    vi: 'Ví dụ: Mặc định ngắn (≤3 câu). Tiếng Việt trừ khi người nhận chỉ biết English. Không dùng emoji.',
    en: 'Example: Default to short replies (≤3 sentences). English only when the recipient prefers it. No emoji.',
  },
  'ai.writingProfile.writingStyle.help': {
    vi: 'Cách bạn muốn AI viết — độ dài, giọng văn, ngôn ngữ, sở thích khác.',
    en: 'How you want the AI to write — length, tone, language, other preferences.',
  },
  'ai.writingProfile.language.label': {
    vi: 'Ngôn ngữ AI ưu tiên',
    en: 'Preferred AI language',
  },
  'ai.writingProfile.language.auto': {
    vi: 'Tự suy luận theo email',
    en: 'Infer from email',
  },
  'ai.writingProfile.language.vi': {
    vi: 'Tiếng Việt',
    en: 'Vietnamese',
  },
  'ai.writingProfile.language.en': {
    vi: 'English',
    en: 'English',
  },
  'ai.writingProfile.save': {
    vi: 'Lưu phong cách',
    en: 'Save profile',
  },
  'ai.writingProfile.saving': {
    vi: 'Đang lưu…',
    en: 'Saving…',
  },
  'ai.writingProfile.saved': {
    vi: 'Đã lưu phong cách AI.',
    en: 'AI writing profile saved.',
  },
  'ai.writingProfile.saveFailed': {
    vi: 'Lưu chưa được. Hãy thử lại.',
    en: 'Save failed. Try again.',
  },
  'ai.writingProfile.charCount': {
    vi: '{current} / {max}',
    en: '{current} / {max}',
  },
} as const;
