export const llmMessages = {
  'llm.byok.title': {
    vi: 'Khóa API cho nhà cung cấp AI',
    en: 'AI provider key',
  },
  'llm.byok.description': {
    vi: 'Dùng khóa riêng của bạn để gọi OpenAI Compatible hoặc Anthropic. Zero Mail chỉ lưu khóa đã mã hóa và không trừ tín dụng nền tảng cho các lượt gọi BYOK.',
    en: 'Use your own key for OpenAI Compatible or Anthropic calls. Zero Mail stores only the encrypted key and does not spend platform credits for BYOK calls.',
  },
  'llm.byok.provider.label': {
    vi: 'Nhà cung cấp',
    en: 'Provider',
  },
  'llm.byok.provider.anthropic': {
    vi: 'Anthropic',
    en: 'Anthropic',
  },
  'llm.byok.provider.openaiCompatible': {
    vi: 'OpenAI Compatible',
    en: 'OpenAI Compatible',
  },
  'llm.byok.endpoint.label': {
    vi: 'Endpoint OpenAI Compatible',
    en: 'OpenAI Compatible endpoint',
  },
  'llm.byok.endpoint.placeholder': {
    vi: 'https://openrouter.ai/api/v1',
    en: 'https://openrouter.ai/api/v1',
  },
  'llm.byok.apiKey.label': {
    vi: 'Khóa API',
    en: 'API key',
  },
  'llm.byok.apiKey.placeholder': {
    vi: 'Dán khóa API',
    en: 'Paste API key',
  },
  'llm.byok.validateCta': {
    vi: 'Kiểm tra khóa API',
    en: 'Validate API key',
  },
  'llm.byok.saveCta': {
    vi: 'Lưu khóa API',
    en: 'Save API key',
  },
  'llm.byok.validating': {
    vi: 'Đang kiểm tra khóa...',
    en: 'Validating key...',
  },
  'llm.byok.saving': {
    vi: 'Đang lưu khóa...',
    en: 'Saving key...',
  },
  'llm.byok.empty.heading': {
    vi: 'Chưa có khóa BYOK',
    en: 'No BYOK key saved',
  },
  'llm.byok.empty.body': {
    vi: 'Chọn nhà cung cấp, dán khóa API, rồi kiểm tra trước khi lưu.',
    en: 'Pick a provider, paste your API key, then validate before saving.',
  },
  'llm.byok.validation.success': {
    vi: 'Khóa đã được kiểm tra. Bạn có thể lưu cấu hình này.',
    en: 'Key validated. You can save this configuration.',
  },
  'llm.byok.validation.invalid': {
    vi: 'Không thể kiểm tra khóa này. Kiểm tra nhà cung cấp, endpoint và khóa API, rồi thử lại.',
    en: 'Could not validate this key. Check provider, endpoint, and API key, then retry.',
  },
  'llm.byok.validation.moreModels': {
    vi: '+{count} mô hình khác',
    en: '+{count} more models',
  },
  'llm.byok.save.success': {
    vi: 'Đã lưu khóa BYOK đã mã hóa. Các lượt gọi AI sẽ dùng khóa này cho đến khi bạn thay đổi.',
    en: 'Encrypted BYOK key saved. AI calls will use this key until you change it.',
  },
  'llm.byok.save.error': {
    vi: 'Không thể lưu khóa đã mã hóa. Tải lại trang rồi thử lại.',
    en: 'Could not save the encrypted key. Reload the page and retry.',
  },
  'llm.byok.existing.badge': {
    vi: 'Đang dùng khóa riêng',
    en: 'Using your own key',
  },
  'llm.byok.existing.creditNote': {
    vi: 'Không trừ tín dụng nền tảng',
    en: 'No platform credit charged',
  },
  'llm.byok.existing.replaceNotice': {
    vi: 'Lưu khóa mới đã kiểm tra sẽ thay thế khóa đã mã hóa hiện tại.',
    en: 'Saving a newly validated key will replace the existing encrypted key.',
  },
  'errors.llm.insufficientCredits.title': {
    vi: 'Tín dụng nền tảng đã hết',
    en: 'Platform credits depleted',
  },
  'errors.llm.insufficientCredits.body': {
    vi: 'Nạp thêm tín dụng hoặc lưu khóa BYOK hợp lệ để tiếp tục gọi AI.',
    en: 'Top up credits or save a valid BYOK key to continue AI calls.',
  },
  'errors.llm.safetyViolation': {
    vi: 'Yêu cầu AI đã bị từ chối vì lý do an toàn.',
    en: 'The AI request was rejected for safety reasons.',
  },
  'errors.llm.safety_violation': {
    vi: 'Yêu cầu AI đã bị từ chối vì lý do an toàn.',
    en: 'The AI request was rejected for safety reasons.',
  },
  'errors.llm.sanitizationFailed': {
    vi: 'Không thể chuẩn hóa nội dung email trước khi gọi AI. Hãy thử lại.',
    en: 'Could not sanitize the email body before calling the AI. Please retry.',
  },
  'errors.llm.sanitization_failed': {
    vi: 'Không thể chuẩn hóa nội dung email trước khi gọi AI. Hãy thử lại.',
    en: 'Could not sanitize the email body before calling the AI. Please retry.',
  },
  'errors.llm.byok.invalid': {
    vi: 'Khóa BYOK không hợp lệ. Kiểm tra nhà cung cấp, endpoint và khóa rồi thử lại.',
    en: 'The BYOK key is invalid. Check provider, endpoint, and key, then retry.',
  },
  'errors.llm.byok.validate_failed': {
    vi: 'Không thể kiểm tra khóa BYOK. Kiểm tra nhà cung cấp và khóa rồi thử lại.',
    en: 'Could not validate the BYOK key. Check provider and key, then retry.',
  },
  'errors.llm.byokValidateFailed': {
    vi: 'Không thể kiểm tra khóa BYOK. Kiểm tra nhà cung cấp và khóa rồi thử lại.',
    en: 'Could not validate the BYOK key. Check provider and key, then retry.',
  },
} as const;
