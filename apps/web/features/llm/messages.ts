export const llmMessages = {
  'llm.byok.title': {
    vi: 'Khóa API cho nhà cung cấp AI',
    en: 'AI provider key',
  },
  'llm.byok.description': {
    vi: 'Dùng khóa riêng của bạn cho OpenRouter, OpenAI, Anthropic, Google GenAI, DeepSeek hoặc endpoint compatible. Zero Mail chỉ lưu khóa đã mã hóa và không trừ tín dụng nền tảng cho các lượt gọi BYOK.',
    en: 'Use your own key for OpenRouter, OpenAI, Anthropic, Google GenAI, DeepSeek, or a compatible endpoint. Zero Mail stores only the encrypted key and does not spend platform credits for BYOK calls.',
  },
  'llm.byok.provider.label': {
    vi: 'Nhà cung cấp',
    en: 'Provider',
  },
  'llm.byok.provider.anthropic': {
    vi: 'Anthropic',
    en: 'Anthropic',
  },
  'llm.byok.provider.anthropicCompatible': {
    vi: 'Anthropic compatible',
    en: 'Anthropic compatible',
  },
  'llm.byok.provider.deepseek': {
    vi: 'DeepSeek',
    en: 'DeepSeek',
  },
  'llm.byok.provider.googleGenAi': {
    vi: 'Google GenAI',
    en: 'Google GenAI',
  },
  'llm.byok.provider.compatibleGroup': {
    vi: 'Endpoint compatible',
    en: 'Compatible endpoints',
  },
  'llm.byok.provider.officialGroup': {
    vi: 'Nhà cung cấp chính thức',
    en: 'Official providers',
  },
  'llm.byok.provider.openai': {
    vi: 'OpenAI',
    en: 'OpenAI',
  },
  'llm.byok.provider.openrouter': {
    vi: 'OpenRouter',
    en: 'OpenRouter',
  },
  'llm.byok.provider.openaiCompatible': {
    vi: 'OpenAI compatible',
    en: 'OpenAI compatible',
  },
  'llm.byok.endpoint.openaiCompatibleLabel': {
    vi: 'Endpoint OpenAI Compatible',
    en: 'OpenAI Compatible endpoint',
  },
  'llm.byok.endpoint.openaiCompatiblePlaceholder': {
    vi: 'https://openrouter.ai/api/v1',
    en: 'https://openrouter.ai/api/v1',
  },
  'llm.byok.endpoint.anthropicCompatibleLabel': {
    vi: 'Endpoint Anthropic compatible',
    en: 'Anthropic compatible endpoint',
  },
  'llm.byok.endpoint.anthropicCompatiblePlaceholder': {
    vi: 'https://api.anthropic.com/v1',
    en: 'https://api.anthropic.com/v1',
  },
  'llm.byok.model.label': {
    vi: 'Model',
    en: 'Model',
  },
  'llm.byok.model.placeholder': {
    vi: 'Nhập mã model',
    en: 'Enter model ID',
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
    vi: 'Chọn nhà cung cấp, model, dán khóa API, rồi kiểm tra trước khi lưu.',
    en: 'Pick a provider and model, paste your API key, then validate before saving.',
  },
  'llm.byok.validation.success': {
    vi: 'Khóa API và cấu hình API hợp lệ. Bạn có thể lưu cấu hình này.',
    en: 'API key and API configuration are valid. You can save this configuration.',
  },
  'llm.byok.validation.invalid': {
    vi: 'Không thể kiểm tra khóa này. Kiểm tra nhà cung cấp, endpoint, model và khóa API, rồi thử lại.',
    en: 'Could not validate this key. Check provider, endpoint, model, and API key, then retry.',
  },
  'llm.byok.validation.connectionFailed': {
    vi: 'Không kết nối được tới endpoint đã chọn. Kiểm tra URL endpoint hoặc mạng rồi thử lại.',
    en: 'Could not reach the selected endpoint. Check the endpoint URL or network and retry.',
  },
  'llm.byok.validation.endpointRejected': {
    vi: 'Endpoint này không hợp lệ hoặc không được phép. Hãy dùng HTTPS public URL đúng của provider.',
    en: 'This endpoint is invalid or not allowed. Use a valid public HTTPS provider URL.',
  },
  'llm.byok.validation.modelNotFound': {
    vi: 'Model này không có trong danh sách model của provider hoặc key chưa có quyền dùng model đó.',
    en: 'This model is not listed by the provider, or the key does not have access to it.',
  },
  'llm.byok.validation.modelRequired': {
    vi: 'Hãy nhập mã model trước khi kiểm tra khóa.',
    en: 'Enter a model ID before validating the key.',
  },
  'llm.byok.validation.timeout': {
    vi: 'Endpoint phản hồi quá chậm. Kiểm tra endpoint rồi thử lại.',
    en: 'The endpoint took too long to respond. Check it and retry.',
  },
  'llm.byok.validation.upstreamRejected': {
    vi: 'Provider đã từ chối yêu cầu. Khóa API có thể sai, bị thu hồi, hoặc không có quyền với model này.',
    en: 'The provider rejected the request. The API key may be wrong, revoked, or missing access to this model.',
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
    vi: 'Đã xảy ra lỗi. Hãy thử lại.',
    en: 'Something went wrong. Try again.',
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
    vi: 'Khóa BYOK không hợp lệ. Kiểm tra nhà cung cấp, endpoint, model và khóa rồi thử lại.',
    en: 'The BYOK key is invalid. Check provider, endpoint, model, and key, then retry.',
  },
  'errors.llm.byok.validate_failed': {
    vi: 'Không thể kiểm tra khóa BYOK. Kiểm tra nhà cung cấp, model và khóa rồi thử lại.',
    en: 'Could not validate the BYOK key. Check provider, model, and key, then retry.',
  },
  'errors.llm.byokValidateFailed': {
    vi: 'Không thể kiểm tra khóa BYOK. Kiểm tra nhà cung cấp, model và khóa rồi thử lại.',
    en: 'Could not validate the BYOK key. Check provider, model, and key, then retry.',
  },
} as const;
