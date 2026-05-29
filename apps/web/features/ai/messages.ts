export const aiMessages = {
  'ai.actions.addSender': {
    vi: '+ Thêm người gửi',
    en: '+ Add sender',
  },
  'ai.actions.addSnippet': {
    vi: '+ Thêm đoạn kiến thức',
    en: '+ Add snippet',
  },
  'ai.actions.cancel': {
    vi: 'Hủy',
    en: 'Cancel',
  },
  'ai.actions.delete': {
    vi: 'Xóa',
    en: 'Delete',
  },
  'ai.actions.edit': {
    vi: 'Sửa',
    en: 'Edit',
  },
  'ai.actions.generateFromSent': {
    vi: 'Tạo từ email đã gửi gần đây',
    en: 'Generate from recent sent emails',
  },
  'ai.actions.remove': {
    vi: 'Gỡ',
    en: 'Remove',
  },
  'ai.actions.replaceKey': {
    vi: 'Thay key',
    en: 'Replace key',
  },
  'ai.actions.save': {
    vi: 'Lưu',
    en: 'Save',
  },
  'ai.actions.set': {
    vi: 'Đặt giá trị',
    en: 'Set',
  },
  'ai.actions.testConnection': {
    vi: 'Kiểm tra kết nối',
    en: 'Test connection',
  },
  'ai.behavior.autoDraftReplies.description': {
    vi: 'AI tự tạo bản nháp trả lời khi quy tắc cho phép.',
    en: 'Let the AI draft replies when rules allow it.',
  },
  'ai.behavior.autoDraftReplies.title': {
    vi: 'Tự động soạn nháp trả lời',
    en: 'Auto-draft replies',
  },
  'ai.behavior.draftConfidence.description': {
    vi: 'Chỉ tạo nháp khi độ tin cậy đạt ngưỡng bạn chọn.',
    en: 'Only draft when confidence reaches your chosen threshold.',
  },
  'ai.behavior.draftConfidence.high': {
    vi: 'HIGH - chỉ khi AI rất chắc chắn',
    en: 'HIGH - only when the AI is very confident',
  },
  'ai.behavior.draftConfidence.low': {
    vi: 'LOW - tạo nháp rộng hơn, bạn kiểm tra lại',
    en: 'LOW - draft more often, you review',
  },
  'ai.behavior.draftConfidence.medium': {
    vi: 'MEDIUM - cân bằng giữa tốc độ và an toàn',
    en: 'MEDIUM - balance speed and caution',
  },
  'ai.behavior.draftConfidence.title': {
    vi: 'Ngưỡng tự tin nháp',
    en: 'Draft confidence threshold',
  },
  'ai.behavior.sensitiveData.description': {
    vi: 'Loại bỏ dữ liệu nhạy cảm khỏi prompt gửi tới LLM.',
    en: 'Strip sensitive data from prompts sent to the LLM.',
  },
  'ai.behavior.sensitiveData.title': {
    vi: 'Bảo vệ dữ liệu nhạy cảm',
    en: 'Sensitive data protection',
  },
  'ai.byok.active.description': {
    vi: 'Bật để dùng key này cho mọi tính năng AI.',
    en: 'Enable this key for all AI features.',
  },
  'ai.byok.active.disabledTooltip': {
    vi: 'Chọn model và kiểm tra kết nối thành công trước khi bật BYOK.',
    en: 'Pick a model and pass the connection test before enabling BYOK.',
  },
  'ai.byok.active.title': {
    vi: 'Đang hoạt động',
    en: 'Active',
  },
  'ai.byok.baseUrl.label': {
    vi: 'Base URL',
    en: 'Base URL',
  },
  'ai.byok.costFooter': {
    vi: 'Chi phí AI 7 ngày qua: {amount}',
    en: 'AI cost last 7 days: {amount}',
  },
  'ai.byok.delete.confirm': {
    vi: 'Xóa key BYOK? Zero Mail sẽ quay lại dùng key nền tảng.',
    en: 'Delete the BYOK key? Zero Mail will return to the platform key.',
  },
  'ai.byok.empty.body': {
    vi: 'Điền key cá nhân, bấm Lưu để mã hóa trên server, rồi Kiểm tra kết nối để tải model.',
    en: 'Enter your personal key, save it for server-side encryption, then test the connection to load models.',
  },
  'ai.byok.empty.title': {
    vi: 'Đang dùng key nền tảng',
    en: 'Using the platform key',
  },
  'ai.byok.key.label': {
    vi: 'API key',
    en: 'API key',
  },
  'ai.byok.key.masked': {
    vi: 'Key đã lưu: ****{lastFourChars}',
    en: 'Saved key: ****{lastFourChars}',
  },
  'ai.byok.model.empty': {
    vi: 'Đã lưu key. Kiểm tra kết nối để tải model',
    en: 'Saved key. Test the connection to load models',
  },
  'ai.byok.model.saveFirst': {
    vi: 'Lưu key trước khi kiểm tra kết nối',
    en: 'Save the key before testing the connection',
  },
  'ai.byok.model.label': {
    vi: 'Model',
    en: 'Model',
  },
  'ai.byok.provider.anthropic': {
    vi: 'Anthropic',
    en: 'Anthropic',
  },
  'ai.byok.provider.deepseek': {
    vi: 'DeepSeek',
    en: 'DeepSeek',
  },
  'ai.byok.provider.google': {
    vi: 'Google',
    en: 'Google',
  },
  'ai.byok.provider.label': {
    vi: 'Nhà cung cấp',
    en: 'Provider',
  },
  'ai.byok.provider.openai': {
    vi: 'OpenAI',
    en: 'OpenAI',
  },
  'ai.byok.replace.confirm': {
    vi: 'Thay key {provider}? Key cũ sẽ bị ghi đè và không khôi phục được. BYOK sẽ tắt đến khi bạn kiểm tra lại và chọn model.',
    en: 'Replace {provider} key? The previous key will be overwritten and cannot be recovered. BYOK will stay off until you test again and pick a model.',
  },
  'ai.byok.status.fail': {
    vi: 'Kiểm tra lỗi',
    en: 'Test failed',
  },
  'ai.byok.status.ok': {
    vi: 'Kết nối OK',
    en: 'Connection OK',
  },
  'ai.byok.test.disabledTooltip': {
    vi: 'Endpoint kiểm tra dùng key đã lưu; hãy lưu key trước.',
    en: 'The test endpoint uses the saved key; save the key first.',
  },
  'ai.byok.title': {
    vi: 'Key cá nhân (BYOK)',
    en: 'Personal key (BYOK)',
  },
  'ai.byok.titleDescription': {
    vi: 'Bật để dùng key của bạn cho mọi tính năng AI. Khi tắt, hệ thống dùng key mặc định của Zero Mail.',
    en: 'Enable your own key for all AI features. When disabled, Zero Mail uses the platform key.',
  },
  'ai.confirm.deleteKnowledge': {
    vi: 'Xóa đoạn kiến thức {title}? Hành động này không thể hoàn tác.',
    en: 'Delete snippet {title}? This cannot be undone.',
  },
  'ai.confirm.removeSafetyNet': {
    vi: 'Gỡ {pattern} khỏi lưới an toàn? AI sẽ lại tự xử lý thư từ địa chỉ này.',
    en: 'Remove {pattern} from the safety net? The AI will resume auto-actioning email from this sender.',
  },
  'ai.empty.knowledge.body': {
    vi: 'Thêm thông tin về bạn hoặc khách hàng để AI dùng khi soạn thư. Bấm + Thêm đoạn kiến thức để bắt đầu.',
    en: 'Add facts about you or customers for the AI to use when drafting. Click + Add snippet to start.',
  },
  'ai.empty.knowledge.title': {
    vi: 'Chưa có đoạn kiến thức nào',
    en: 'No snippets yet',
  },
  'ai.empty.safetyNet.body': {
    vi: 'Thêm email (vd. ceo@acme.com) hoặc domain (vd. @acme.com) để AI luôn để bạn xử lý tay.',
    en: 'Add an email (e.g. ceo@acme.com) or a domain (e.g. @acme.com) so the AI always leaves these to you.',
  },
  'ai.empty.safetyNet.title': {
    vi: 'Chưa có người gửi nào',
    en: 'No senders yet',
  },
  'ai.empty.sent.body': {
    vi: 'Hộp thư Đã gửi trống nên không tạo được mẫu giọng văn. Hãy viết thử vài email rồi quay lại sau.',
    en: 'Your Sent folder is empty so no style sample could be generated. Send a few emails and come back.',
  },
  'ai.empty.sent.title': {
    vi: 'Không tìm thấy email đã gửi',
    en: 'No sent emails found',
  },
  'ai.knowledge.content.label': {
    vi: 'Nội dung',
    en: 'Content',
  },
  'ai.knowledge.dialog.addTitle': {
    vi: 'Thêm đoạn kiến thức',
    en: 'Add snippet',
  },
  'ai.knowledge.dialog.editTitle': {
    vi: 'Sửa đoạn kiến thức',
    en: 'Edit snippet',
  },
  'ai.knowledge.table.delete': {
    vi: 'Xóa',
    en: 'Delete',
  },
  'ai.knowledge.table.edit': {
    vi: 'Sửa',
    en: 'Edit',
  },
  'ai.knowledge.table.lastUpdated': {
    vi: 'Cập nhật',
    en: 'Last updated',
  },
  'ai.knowledge.table.title': {
    vi: 'Tiêu đề',
    en: 'Title',
  },
  'ai.knowledge.title.description': {
    vi: 'Thông tin cố định AI nên nhớ khi soạn thư.',
    en: 'Stable facts the AI should remember while drafting.',
  },
  'ai.knowledge.title.label': {
    vi: 'Tiêu đề',
    en: 'Title',
  },
  'ai.knowledge.title.text': {
    vi: 'Kho kiến thức',
    en: 'Knowledge',
  },
  'ai.page.description': {
    vi: 'Tinh chỉnh giọng văn, hành vi, và nhà cung cấp AI.',
    en: 'Tune your voice, behavior, and AI provider.',
  },
  'ai.page.title': {
    vi: 'Cài đặt AI',
    en: 'AI settings',
  },
  'ai.safetyNet.add.placeholder': {
    vi: 'ceo@acme.com hoặc @acme.com',
    en: 'ceo@acme.com or @acme.com',
  },
  'ai.safetyNet.autoSend.description': {
    vi: 'Cho phép rule tự gửi khi vượt qua toàn bộ cổng an toàn.',
    en: 'Allow rules to send automatically after all safety gates pass.',
  },
  'ai.safetyNet.autoSend.title': {
    vi: 'Tự động gửi theo rule',
    en: 'Auto-send rules',
  },
  'ai.safetyNet.createdBy.system': {
    vi: 'Hệ thống',
    en: 'System',
  },
  'ai.safetyNet.createdBy.user': {
    vi: 'Bạn',
    en: 'You',
  },
  'ai.safetyNet.deleteDisabled': {
    vi: 'Người gửi này do hệ thống tự thêm nên không thể xóa.',
    en: 'This sender was added by the system and cannot be deleted.',
  },
  'ai.safetyNet.kind.domain': {
    vi: 'Domain',
    en: 'Domain',
  },
  'ai.safetyNet.kind.email': {
    vi: 'Email',
    en: 'Email',
  },
  'ai.safetyNet.protectedSenders.description': {
    vi: 'Email hoặc domain mà AI không bao giờ tự xử lý.',
    en: 'Emails or domains the AI never auto-actions.',
  },
  'ai.safetyNet.protectedSenders.title': {
    vi: 'Người gửi được bảo vệ',
    en: 'Protected senders',
  },
  'ai.safetyNet.tip': {
    vi: 'Mẹo: dùng @acme.com để bảo vệ toàn bộ domain.',
    en: 'Tip: use @acme.com to protect an entire domain.',
  },
  'ai.sections.behavior.helper': {
    vi: 'Khi nào AI hành động tự động',
    en: 'When the AI acts automatically',
  },
  'ai.sections.behavior.title': {
    vi: 'Hành vi trợ lý',
    en: 'Behavior',
  },
  'ai.sections.provider.helper': {
    vi: 'Key cá nhân và chi phí AI',
    en: 'Personal keys and AI cost',
  },
  'ai.sections.provider.title': {
    vi: 'Nhà cung cấp AI',
    en: 'AI Provider',
  },
  'ai.sections.safetyNet.helper': {
    vi: 'Người gửi mà AI không bao giờ tự xử lý',
    en: 'Senders the AI never auto-actions',
  },
  'ai.sections.safetyNet.title': {
    vi: 'Lưới an toàn',
    en: 'Safety net',
  },
  'ai.sections.updates.helper': {
    vi: 'Tóm tắt hằng ngày và chế độ shadow',
    en: 'Daily digest and shadow mode',
  },
  'ai.sections.updates.title': {
    vi: 'Cập nhật',
    en: 'Updates',
  },
  'ai.sections.voice.helper': {
    vi: 'Cách AI viết thay bạn',
    en: 'How the AI writes for you',
  },
  'ai.sections.voice.title': {
    vi: 'Giọng văn của bạn',
    en: 'Your voice',
  },
  'ai.senders.add': {
    vi: 'Bảo vệ',
    en: 'Protect',
  },
  'ai.senders.addFailed': {
    vi: 'Chưa thêm được người gửi. Hãy thử lại.',
    en: 'Could not protect this sender. Try again.',
  },
  'ai.senders.added': {
    vi: 'Đã bảo vệ {email}',
    en: 'Protected {email}',
  },
  'ai.senders.adding': {
    vi: 'Đang thêm...',
    en: 'Adding...',
  },
  'ai.senders.description': {
    vi: 'Email từ những người gửi này luôn được giữ nguyên - không gắn nhãn, không lưu trữ, không soạn nháp tự động, dù quy tắc có khớp đến đâu.',
    en: 'Mail from these senders is always left alone - no labels, no archive, no auto-drafts, no matter how strongly a rule matches.',
  },
  'ai.senders.heading': {
    vi: 'Người gửi được bảo vệ',
    en: 'Protected senders',
  },
  'ai.senders.inputLabel': {
    vi: 'Email người gửi cần bảo vệ',
    en: 'Sender email to protect',
  },
  'ai.senders.inputPlaceholder': {
    vi: 'sep@congty.com',
    en: 'boss@company.com',
  },
  'ai.senders.invalidEmail': {
    vi: 'Email không hợp lệ.',
    en: 'Invalid email address.',
  },
  'ai.toast.aiPreferenceSaved': {
    vi: 'Đã lưu lựa chọn AI',
    en: 'AI preference saved',
  },
  'ai.toast.behaviorSaved': {
    vi: 'Đã lưu hành vi',
    en: 'Behavior saved',
  },
  'ai.toast.byokDeleted': {
    vi: 'Đã xóa key cá nhân',
    en: 'Personal key deleted',
  },
  'ai.toast.byokKeySaved': {
    vi: 'Đã lưu key (không hiển thị lại)',
    en: 'Key saved (will not be shown again)',
  },
  'ai.toast.byokTestOk': {
    vi: 'Key hoạt động bình thường',
    en: 'Key works',
  },
  'ai.toast.genericFailure': {
    vi: 'Không lưu được. Thử lại nhé.',
    en: "Couldn't save. Please try again.",
  },
  'ai.toast.safetyNetAdded': {
    vi: 'Đã thêm người gửi vào lưới an toàn',
    en: 'Sender added to safety net',
  },
  'ai.toast.safetyNetRemoved': {
    vi: 'Đã gỡ người gửi',
    en: 'Sender removed',
  },
  'ai.toast.snippetAdded': {
    vi: 'Đã thêm đoạn kiến thức',
    en: 'Snippet added',
  },
  'ai.toast.snippetDeleted': {
    vi: 'Đã xóa đoạn kiến thức',
    en: 'Snippet deleted',
  },
  'ai.toast.snippetUpdated': {
    vi: 'Đã cập nhật đoạn kiến thức',
    en: 'Snippet updated',
  },
  'ai.toast.voiceGenerated': {
    vi: 'Đã tạo bản nháp - xem lại trước khi lưu',
    en: 'Draft generated - review before saving',
  },
  'ai.toast.voiceSaved': {
    vi: 'Đã lưu giọng văn',
    en: 'Voice saved',
  },
  'ai.updates.dailyDigest.description': {
    vi: 'Nhận một bản tóm tắt email hằng ngày.',
    en: 'Receive one daily email digest.',
  },
  'ai.updates.dailyDigest.title': {
    vi: 'Tóm tắt hằng ngày',
    en: 'Daily digest',
  },
  'ai.updates.pauseTriage.description': {
    vi: 'Tạm dừng tự động xử lý nhưng vẫn giữ dữ liệu cấu hình.',
    en: 'Pause automatic triage while keeping your configuration.',
  },
  'ai.updates.pauseTriage.title': {
    vi: 'Tạm dừng triage',
    en: 'Pause triage',
  },
  'ai.voice.language.description': {
    vi: 'Ngôn ngữ mặc định khi AI soạn nháp.',
    en: 'Default language for AI drafts.',
  },
  'ai.voice.language.english': {
    vi: 'English',
    en: 'English',
  },
  'ai.voice.language.title': {
    vi: 'Ngôn ngữ AI viết',
    en: 'AI output language',
  },
  'ai.voice.language.vietnamese': {
    vi: 'Tiếng Việt',
    en: 'Vietnamese',
  },
  'ai.voice.personalInstructions.description': {
    vi: 'Những điều AI cần biết về bạn trước khi soạn thư.',
    en: 'What the AI should know about you before drafting.',
  },
  'ai.voice.personalInstructions.placeholder': {
    vi: 'Ví dụ: ưu tiên trả lời ngắn, lịch sự, nêu rõ bước tiếp theo...',
    en: 'Example: keep replies concise, courteous, and explicit about next steps...',
  },
  'ai.voice.personalInstructions.title': {
    vi: 'Về tôi (hướng dẫn cá nhân)',
    en: 'About me (personal instructions)',
  },
  'ai.voice.signature.description': {
    vi: 'Chữ ký AI có thể chèn vào bản nháp khi phù hợp.',
    en: 'A signature the AI can include in drafts when appropriate.',
  },
  'ai.voice.signature.placeholder': {
    vi: 'Tên, chức danh, số điện thoại...',
    en: 'Name, title, phone number...',
  },
  'ai.voice.signature.title': {
    vi: 'Chữ ký email',
    en: 'Email signature',
  },
  'ai.voice.tone.casual': {
    vi: 'Casual',
    en: 'Casual',
  },
  'ai.voice.tone.custom': {
    vi: 'Custom',
    en: 'Custom',
  },
  'ai.voice.tone.description': {
    vi: 'Tone mặc định khi AI tạo bản nháp.',
    en: 'Default tone for AI drafts.',
  },
  'ai.voice.tone.formal': {
    vi: 'Formal',
    en: 'Formal',
  },
  'ai.voice.tone.friendly': {
    vi: 'Friendly',
    en: 'Friendly',
  },
  'ai.voice.tone.professional': {
    vi: 'Professional',
    en: 'Professional',
  },
  'ai.voice.tone.title': {
    vi: 'Tone giọng văn',
    en: 'Tone',
  },
  'ai.voice.writingStyle.description': {
    vi: 'Mô tả cách bạn thường viết để AI bắt chước an toàn hơn.',
    en: 'Describe how you usually write so the AI can mirror you more safely.',
  },
  'ai.voice.writingStyle.placeholder': {
    vi: 'Viết 200-500 từ về cách bạn chào hỏi, giải thích, từ chối, và kết thúc email...',
    en: 'Write 200-500 words about how you greet, explain, decline, and close emails...',
  },
  'ai.voice.writingStyle.title': {
    vi: 'Phong cách viết',
    en: 'Writing style',
  },
  'ai.voice.wordCount': {
    vi: '{count} từ',
    en: '{count} words',
  },
  'audit.badge.blockedBySafetyNet': {
    vi: 'Chặn bởi lưới an toàn: {pattern}',
    en: 'Blocked by safety net: {pattern}',
  },
  'errors.ai.byok.base_url_host_private': {
    vi: 'Base URL không được trỏ tới mạng nội bộ hoặc localhost.',
    en: 'Base URL cannot point to a private network or localhost.',
  },
  'errors.ai.byok.base_url_host_unresolvable': {
    vi: 'Không phân giải được host của Base URL.',
    en: 'Base URL host could not be resolved.',
  },
  'errors.ai.byok.base_url_not_https': {
    vi: 'Base URL phải bắt đầu bằng https://',
    en: 'Base URL must start with https://',
  },
  'errors.ai.byok.base_url_not_supported_for_provider': {
    vi: 'Base URL này không phù hợp với nhà cung cấp đã chọn.',
    en: 'This Base URL is not supported for the selected provider.',
  },
  'errors.ai.byok.base_url_port_not_allowed': {
    vi: 'Base URL chỉ được dùng cổng HTTPS mặc định.',
    en: 'Base URL can only use the default HTTPS port.',
  },
  'errors.ai.byok.model_not_in_last_test': {
    vi: 'Model này không nằm trong lần kiểm tra kết nối gần nhất.',
    en: 'This model was not returned by the latest connection test.',
  },
  'errors.ai.byok.no_model_picked': {
    vi: 'Hãy chọn model và kiểm tra kết nối thành công trước khi bật BYOK.',
    en: 'Pick a model and pass the connection test before enabling BYOK.',
  },
  'errors.ai.byok.no_row': {
    vi: 'Lưu trước rồi kiểm tra lại.',
    en: 'Save first, then test again.',
  },
  'errors.ai.byok.provider_not_allowed': {
    vi: 'Nhà cung cấp này không hỗ trợ BYOK.',
    en: 'BYOK is not supported for this provider.',
  },
  'errors.ai.byok.rate_limit_unavailable': {
    vi: 'Tạm thời chưa kiểm tra được giới hạn. Thử lại sau.',
    en: 'Rate limiting is temporarily unavailable. Try again later.',
  },
  'errors.ai.byok.rate_limited': {
    vi: 'Bạn thao tác quá nhiều lần. Thử lại sau.',
    en: 'Too many attempts. Try again later.',
  },
  'errors.ai.byok.test_connection.rate_limited': {
    vi: 'Bạn đã kiểm tra quá nhiều lần. Thử lại sau 1 giờ.',
    en: 'Too many test attempts. Try again in 1 hour.',
  },
  'errors.ai.test_connection.rate_limited': {
    vi: 'Bạn đã kiểm tra quá nhiều lần. Thử lại sau 1 giờ.',
    en: 'Too many test attempts. Try again in 1 hour.',
  },
  'errors.behavior.draft_confidence.invalid': {
    vi: 'Ngưỡng tự tin nháp không hợp lệ.',
    en: 'Invalid draft confidence threshold.',
  },
  'errors.knowledge.not_found': {
    vi: 'Không tìm thấy đoạn kiến thức này. Hãy tải lại danh sách.',
    en: 'This snippet could not be found. Reload the list.',
  },
  'errors.knowledge.title.duplicate': {
    vi: 'Đã có đoạn kiến thức với tiêu đề này.',
    en: 'A snippet with this title already exists.',
  },
  'errors.safety_net.not_found': {
    vi: 'Không tìm thấy người gửi trong lưới an toàn. Hãy tải lại danh sách.',
    en: 'This safety-net sender could not be found. Reload the list.',
  },
  'errors.safety_net.observation_not_deletable': {
    vi: 'Không thể xóa người gửi do hệ thống tự thêm.',
    en: 'Cannot delete a system-observed sender.',
  },
  'errors.safety_net.pattern_invalid': {
    vi: 'Mẫu người gửi không hợp lệ. Dùng email hoặc domain như @acme.com.',
    en: 'Invalid sender pattern. Use an email or a domain like @acme.com.',
  },
  'errors.voice.generate.failed': {
    vi: 'Chưa tạo được giọng văn từ email đã gửi. Thử lại sau.',
    en: 'Could not generate a voice sample from sent emails. Try again later.',
  },
  'errors.voice.generate.gmail_read_failed': {
    vi: 'Không đọc được email đã gửi lúc này. Thử lại sau.',
    en: 'Could not read sent emails right now. Try again later.',
  },
  'errors.voice.generate.rate_limited': {
    vi: 'Đã đạt giới hạn 3 lần/giờ. Thử lại sau.',
    en: 'Reached the 3/hour limit. Try again later.',
  },
  'errors.voice.personal_instructions.too_long': {
    vi: 'Hướng dẫn cá nhân không được vượt 2000 ký tự.',
    en: 'Personal instructions cannot exceed 2000 characters.',
  },
  'errors.voice.tone_preset.invalid': {
    vi: 'Tone không hợp lệ.',
    en: 'Invalid tone preset.',
  },
  'errors.voice.writing_style.too_long': {
    vi: 'Mô tả giọng văn không được vượt 500 từ.',
    en: 'Writing style cannot exceed 500 words.',
  },
  'errors.voice.writing_style.too_short': {
    vi: 'Mô tả giọng văn cần ít nhất 200 từ.',
    en: 'Writing style needs at least 200 words.',
  },
} as const;
