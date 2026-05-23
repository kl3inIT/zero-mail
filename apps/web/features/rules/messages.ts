export const rulesMessages = {
  'rules.page.title': {
    vi: 'Quy tắc tự động',
    en: 'Automation rules',
  },
  'rules.page.intro': {
    vi: 'Mỗi quy tắc nói rõ email nào cần nhận diện và Zero Mail được phép làm gì. Quy tắc mới luôn tắt cho đến khi bạn chạy thử.',
    en: 'Each rule defines which emails to match and what Zero Mail is allowed to do. New rules stay off until previewed.',
  },
  'rules.page.safetyNote': {
    vi: 'Mô tả tự nhiên chỉ là cách nhập nhanh. Khi lưu, Zero Mail giữ cấu trúc điều kiện và hành động để bạn kiểm tra lại; các hành động nguy hiểm vẫn bị khóa.',
    en: 'Natural language is only a quick input method. Saved rules keep a reviewable criteria and action structure, and unsafe actions stay locked.',
  },
  'rules.tabs.label': {
    vi: 'Chế độ trang quy tắc',
    en: 'Rules page mode',
  },
  'rules.tabs.list': {
    vi: 'Danh sách quy tắc',
    en: 'Rule list',
  },
  'rules.tabs.test': {
    vi: 'Kiểm tra quy tắc',
    en: 'Test rules',
  },
  'rules.tabs.history': {
    vi: 'Lịch sử',
    en: 'History',
  },
  'rules.tabs.historyIntro': {
    vi: 'Mọi hành động AI đã chạy trên Gmail của bạn, kèm rule khớp, lý do và nút hoàn tác trong 30 ngày.',
    en: 'Every AI action applied to your Gmail, with the matching rule, reason, and a 30-day undo window.',
  },
  'rules.tabs.testIntro': {
    vi: 'Mặc định sẽ thử trên {count} quy tắc đang bật.',
    en: 'By default, testing runs on {count} enabled rules.',
  },
  'rules.tabs.testModeLabel': {
    vi: 'Chế độ kiểm tra',
    en: 'Test mode',
  },
  'rules.tabs.testCustom': {
    vi: 'Email tự soạn',
    en: 'Custom email',
  },
  'rules.tabs.testGmail': {
    vi: 'Email Gmail thật',
    en: 'Real Gmail',
  },
  'rules.tabs.freeBadge': {
    vi: 'Miễn phí',
    en: 'Free',
  },
  'rules.tabs.creditBadge': {
    vi: 'Tốn credit',
    en: 'Uses credits',
  },
  'rules.tabs.gmailCreditWarningTitle': {
    vi: 'Test trên Gmail thật sẽ tốn credit',
    en: 'Real Gmail test uses credits',
  },
  'rules.tabs.gmailCreditWarningBody': {
    vi: 'Nếu quy tắc có matcher cần LLM phân loại, mỗi lần chạy thử sẽ tốn credit nền tảng. Test miễn phí bằng email tự soạn ở tab bên cạnh.',
    en: 'When a rule uses semantic LLM matchers, each test consumes platform credits. The Custom email tab tests rule logic for free.',
  },
  'rules.list.title': {
    vi: 'Danh sách quy tắc',
    en: 'Rule list',
  },
  'rules.list.subtitle': {
    vi: 'Xem nhanh từng quy tắc theo tên, email phù hợp và hành động được phép.',
    en: 'Scan each rule by name, matched email, and allowed actions.',
  },
  'rules.list.column.enabled': {
    vi: 'Bật',
    en: 'Enabled',
  },
  'rules.list.column.name': {
    vi: 'Tên',
    en: 'Name',
  },
  'rules.list.column.selectAll': {
    vi: 'Chọn tất cả quy tắc để thử',
    en: 'Select all rules for testing',
  },
  'rules.list.column.selectRow': {
    vi: 'Chọn quy tắc {name}',
    en: 'Select rule {name}',
  },
  'rules.list.actions': {
    vi: 'Thao tác với quy tắc',
    en: 'Rule actions',
  },
  'rules.list.empty.heading': {
    vi: 'Chưa có quy tắc',
    en: 'No rules yet',
  },
  'rules.list.empty.body': {
    vi: 'Tạo quy tắc đầu tiên hoặc dùng mẫu Gmail có sẵn. Quy tắc mới luôn được lưu ở trạng thái tắt cho đến khi bạn chạy thử.',
    en: 'Write your first rule or start from a Gmail template. New rules stay disabled until you preview them.',
  },
  'rules.list.templateBadge': {
    vi: 'Mẫu',
    en: 'Template',
  },
  'rules.list.customizedBadge': {
    vi: 'Đã tùy chỉnh',
    en: 'Customized',
  },
  'rules.list.when': {
    vi: 'Email phù hợp',
    en: 'Matched email',
  },
  'rules.list.then': {
    vi: 'Hành động',
    en: 'Actions',
  },
  'rules.list.noWhen': {
    vi: 'Chưa có điều kiện rõ ràng',
    en: 'No clear criteria yet',
  },
  'rules.list.noThen': {
    vi: 'Chưa có hành động',
    en: 'No action yet',
  },
  'rules.list.edit': {
    vi: 'Sửa quy tắc',
    en: 'Edit rule',
  },
  'rules.list.delete': {
    vi: 'Xóa quy tắc',
    en: 'Delete rule',
  },
  'rules.composer.title': {
    vi: 'Tạo quy tắc xử lý email',
    en: 'Create email rule',
  },
  'rules.composer.tabsLabel': {
    vi: 'Cách tạo quy tắc',
    en: 'Rule authoring mode',
  },
  'rules.composer.tab.describe': {
    vi: 'Viết bằng mô tả',
    en: 'Describe',
  },
  'rules.composer.tab.manual': {
    vi: 'Xem & sửa',
    en: 'Review & edit',
  },
  'rules.composer.sourceLabel': {
    vi: 'Bạn muốn Zero Mail xử lý email nào và làm gì?',
    en: 'Which emails should Zero Mail match, and what should it do?',
  },
  'rules.composer.sourcePlaceholder': {
    vi: 'Mô tả email cần khớp và hành động Zero Mail nên làm',
    en: 'Describe the emails to match and what Zero Mail should do',
  },
  'rules.composer.compileCta': {
    vi: 'Chuyển thành quy tắc',
    en: 'Convert to rule',
  },
  'rules.composer.compiling': {
    vi: 'Đang kiểm tra...',
    en: 'Checking...',
  },
  'rules.composer.saveDisabledCta': {
    vi: 'Lưu (giữ tắt cho đến khi chạy thử)',
    en: 'Save (stays off until preview)',
  },
  'rules.composer.saving': {
    vi: 'Đang lưu...',
    en: 'Saving...',
  },
  'rules.composer.answerClarification': {
    vi: 'Gửi câu trả lời',
    en: 'Send answer',
  },
  'rules.composer.answerLabel': {
    vi: 'Câu trả lời của bạn',
    en: 'Your answer',
  },
  'rules.composer.compiledReview': {
    vi: 'Quy tắc sẽ được lưu',
    en: 'Rule to save',
  },
  'rules.composer.matcherReview': {
    vi: 'Email phù hợp',
    en: 'Matched email',
  },
  'rules.composer.actionReview': {
    vi: 'Zero Mail sẽ làm',
    en: 'Allowed actions',
  },
  'rules.composer.allowedActionsNote': {
    vi: 'Quy tắc được lưu dưới dạng điều kiện và hành động có cấu trúc. Hành động gửi outbound chạy qua cổng an toàn và cài đặt Auto-send rules.',
    en: 'Rules are saved as structured criteria and actions. Outbound sends run through safety gates and the Auto-send rules setting.',
  },
  'rules.composer.invalid': {
    vi: 'Quy tắc này chưa thể lưu. Hãy sửa cách diễn đạt hoặc trả lời câu hỏi làm rõ.',
    en: 'This rule is not ready to save. Edit the wording or answer the clarification.',
  },
  'rules.composer.examplesHint': {
    vi: 'Gợi ý từ catalog',
    en: 'Catalog examples',
  },
  'rules.composer.examples.title': {
    vi: 'Chọn từ ví dụ',
    en: 'Choose from examples',
  },
  'rules.composer.examples.body': {
    vi: 'Chọn nhóm phù hợp rồi bấm một ví dụ để thay toàn bộ nội dung ô mô tả.',
    en: 'Choose a persona, then click an example to replace the whole description.',
  },
  'rules.composer.examples.empty': {
    vi: 'Chưa có ví dụ đang bật cho nhóm này.',
    en: 'No enabled examples for this persona yet.',
  },
  'rules.composer.examples.error': {
    vi: 'Chưa tải được ví dụ. Bạn vẫn có thể tự viết quy tắc.',
    en: 'Examples are not available right now. You can still write your own rule.',
  },
  'rules.composer.personaLabel': {
    vi: 'Nhóm ví dụ',
    en: 'Example persona',
  },
  'rules.composer.personaFallback': {
    vi: 'Chọn nhóm',
    en: 'Choose persona',
  },
  'rules.composer.newRuleCta': {
    vi: 'Tạo quy tắc',
    en: 'Create rule',
  },
  'rules.manual.nameLabel': {
    vi: 'Tên quy tắc',
    en: 'Rule name',
  },
  'rules.manual.refineLabel': {
    vi: 'Muốn chỉnh thêm bằng mô tả?',
    en: 'Refine with another instruction',
  },
  'rules.manual.refinePlaceholder': {
    vi: 'Ví dụ: trừ email quảng cáo khóa học, hoặc chỉ áp dụng cho deadline và bài tập',
    en: 'Example: exclude course ads, or only match deadlines and assignments',
  },
  'rules.manual.refineCta': {
    vi: 'Cập nhật form',
    en: 'Update form',
  },
  'rules.manual.namePlaceholder': {
    vi: 'Ví dụ: Lưu biên lai Stripe',
    en: 'Example: Archive Stripe receipts',
  },
  'rules.manual.whenTitle': {
    vi: 'Email phù hợp',
    en: 'Matched email',
  },
  'rules.manual.whenBody': {
    vi: 'Chọn dấu hiệu để Zero Mail nhận diện email trước khi hành động.',
    en: 'Choose the signals Zero Mail checks before taking action.',
  },
  'rules.manual.thenTitle': {
    vi: 'Zero Mail sẽ làm',
    en: 'Allowed actions',
  },
  'rules.manual.thenBody': {
    vi: 'Chọn hành động có cấu trúc. Các hành động gửi outbound sẽ chạy qua cài đặt Auto-send rules và cổng an toàn.',
    en: 'Choose structured actions. Outbound sends use the Auto-send rules setting and safety gateway.',
  },
  'rules.manual.operator.all': {
    vi: 'Khớp tất cả',
    en: 'Match all',
  },
  'rules.manual.operator.any': {
    vi: 'Khớp bất kỳ',
    en: 'Match any',
  },
  'rules.manual.addCondition': {
    vi: 'Thêm điều kiện',
    en: 'Add condition',
  },
  'rules.manual.addAction': {
    vi: 'Thêm hành động',
    en: 'Add action',
  },
  'rules.manual.removeCondition': {
    vi: 'Xóa điều kiện',
    en: 'Remove condition',
  },
  'rules.manual.removeAction': {
    vi: 'Xóa hành động',
    en: 'Remove action',
  },
  'rules.manual.noValueNeeded': {
    vi: 'Không cần nhập thêm',
    en: 'No value needed',
  },
  'rules.manual.advancedTitle': {
    vi: 'Cách gửi outbound',
    en: 'Outbound behavior',
  },
  'rules.manual.outbound.autoSend': {
    vi: 'Auto-send rules bật thì gửi tự động',
    en: 'Auto-send rules sends automatically',
  },
  'rules.manual.outbound.fallbackDraft': {
    vi: 'Không đạt gate thì lưu bản nháp',
    en: 'Gate failures save a draft',
  },
  'rules.manual.outbound.deleteDisabled': {
    vi: 'Không tự động xóa vĩnh viễn',
    en: 'Permanent delete is disabled',
  },
  'rules.manual.outbound.webhookDisabled': {
    vi: 'Webhook chưa hỗ trợ',
    en: 'Webhook is not supported',
  },
  'rules.manual.structuredPreview': {
    vi: 'Quy tắc sẽ được lưu',
    en: 'Rule to save',
  },
  'rules.manual.saveHint': {
    vi: 'Quy tắc mới hoặc vừa sửa luôn được lưu ở trạng thái tắt cho đến khi bạn chạy thử.',
    en: 'New or edited rules are saved off until you preview them.',
  },
  'rules.manual.condition.SENDER_DOMAIN': {
    vi: 'Tên miền người gửi',
    en: 'Sender domain',
  },
  'rules.manual.condition.SENDER_EMAIL': {
    vi: 'Email người gửi',
    en: 'Sender email',
  },
  'rules.manual.condition.RECIPIENT_TO': {
    vi: 'Gửi tới',
    en: 'To recipient',
  },
  'rules.manual.condition.SUBJECT_CONTAINS': {
    vi: 'Tiêu đề chứa',
    en: 'Subject contains',
  },
  'rules.manual.condition.GMAIL_LABEL_PRESENT': {
    vi: 'Có nhãn Gmail',
    en: 'Gmail label present',
  },
  'rules.manual.condition.HAS_ATTACHMENT': {
    vi: 'Có tệp đính kèm',
    en: 'Has attachment',
  },
  'rules.manual.condition.NEWSLETTER_INDICATOR': {
    vi: 'Có dấu hiệu newsletter',
    en: 'Newsletter indicator',
  },
  'rules.manual.condition.SEMANTIC_INTENT': {
    vi: 'Nội dung cần nhận diện',
    en: 'Email meaning',
  },
  'rules.manual.conditionPlaceholder.SENDER_DOMAIN': {
    vi: 'stripe.com',
    en: 'stripe.com',
  },
  'rules.manual.conditionPlaceholder.SENDER_EMAIL': {
    vi: 'billing@stripe.com',
    en: 'billing@stripe.com',
  },
  'rules.manual.conditionPlaceholder.RECIPIENT_TO': {
    vi: 'founder@example.com',
    en: 'founder@example.com',
  },
  'rules.manual.conditionPlaceholder.SUBJECT_CONTAINS': {
    vi: 'receipt',
    en: 'receipt',
  },
  'rules.manual.conditionPlaceholder.GMAIL_LABEL_PRESENT': {
    vi: 'Finance',
    en: 'Finance',
  },
  'rules.manual.conditionPlaceholder.HAS_ATTACHMENT': {
    vi: 'Không cần nhập thêm',
    en: 'No value needed',
  },
  'rules.manual.conditionPlaceholder.NEWSLETTER_INDICATOR': {
    vi: 'Không cần nhập thêm',
    en: 'No value needed',
  },
  'rules.manual.conditionPlaceholder.SEMANTIC_INTENT': {
    vi: 'Email liên quan đến học tập',
    en: 'Email about studying',
  },
  'rules.manual.action.label': {
    vi: 'Gắn nhãn',
    en: 'Label',
  },
  'rules.manual.action.archive': {
    vi: 'Lưu trữ',
    en: 'Archive',
  },
  'rules.manual.action.save_draft': {
    vi: 'Lưu bản nháp',
    en: 'Save draft',
  },
  'rules.manual.action.mark_read': {
    vi: 'Đánh dấu đã đọc',
    en: 'Mark read',
  },
  'rules.manual.action.star': {
    vi: 'Gắn sao',
    en: 'Star',
  },
  'rules.manual.action.add_to_digest': {
    vi: 'Thêm vào digest',
    en: 'Add to digest',
  },
  'rules.manual.action.mark_spam': {
    vi: 'Đánh dấu spam',
    en: 'Mark spam',
  },
  'rules.manual.action.send_reply': {
    vi: 'Gửi trả lời',
    en: 'Send reply',
  },
  'rules.manual.action.forward_email': {
    vi: 'Chuyển tiếp',
    en: 'Forward',
  },
  'rules.manual.action.send_email': {
    vi: 'Gửi email',
    en: 'Send email',
  },
  'rules.manual.actionPlaceholder.label': {
    vi: 'Finance',
    en: 'Finance',
  },
  'rules.manual.actionPlaceholder.archive': {
    vi: 'Không cần nhập thêm',
    en: 'No value needed',
  },
  'rules.manual.actionPlaceholder.save_draft': {
    vi: 'Soạn bản nháp ngắn để xin file PDF hóa đơn',
    en: 'Draft a short reply asking for the invoice PDF',
  },
  'rules.manual.actionPlaceholder.mark_read': {
    vi: 'Không cần nhập thêm',
    en: 'No value needed',
  },
  'rules.manual.actionPlaceholder.star': {
    vi: 'Không cần nhập thêm',
    en: 'No value needed',
  },
  'rules.manual.actionPlaceholder.add_to_digest': {
    vi: 'Không cần nhập thêm',
    en: 'No value needed',
  },
  'rules.manual.actionPlaceholder.mark_spam': {
    vi: 'Không cần nhập thêm',
    en: 'No value needed',
  },
  'rules.manual.actionPlaceholder.send_reply': {
    vi: 'Gửi lời cảm ơn ngắn và nói tôi sẽ phản hồi sau',
    en: 'Send a short thank-you and say I will follow up',
  },
  'rules.manual.actionPlaceholder.forward_email': {
    vi: 'ops@example.com',
    en: 'ops@example.com',
  },
  'rules.manual.actionPlaceholder.forwardInstruction': {
    vi: 'Chuyển tiếp kèm ghi chú ngắn',
    en: 'Forward with a short note',
  },
  'rules.manual.actionPlaceholder.send_email': {
    vi: 'founder@example.com',
    en: 'founder@example.com',
  },
  'rules.manual.actionPlaceholder.cc': {
    vi: 'cc@example.com',
    en: 'cc@example.com',
  },
  'rules.manual.actionPlaceholder.bcc': {
    vi: 'bcc@example.com',
    en: 'bcc@example.com',
  },
  'rules.manual.actionPlaceholder.subject': {
    vi: 'Cập nhật hôm nay',
    en: 'Today update',
  },
  'rules.manual.actionPlaceholder.body': {
    vi: 'Nội dung email do bạn muốn Zero Mail gửi.',
    en: 'Email body you want Zero Mail to send.',
  },
  'rules.manual.actionField.recipients': {
    vi: 'Người nhận',
    en: 'Recipients',
  },
  'rules.manual.actionField.instruction': {
    vi: 'Chỉ dẫn',
    en: 'Instruction',
  },
  'rules.manual.actionField.to': {
    vi: 'Gửi tới',
    en: 'To',
  },
  'rules.manual.actionField.cc': {
    vi: 'Cc',
    en: 'Cc',
  },
  'rules.manual.actionField.bcc': {
    vi: 'Bcc',
    en: 'Bcc',
  },
  'rules.manual.actionField.subject': {
    vi: 'Tiêu đề',
    en: 'Subject',
  },
  'rules.manual.actionField.body': {
    vi: 'Nội dung',
    en: 'Body',
  },
  'rules.preview.title': {
    vi: 'Kiểm tra trên Gmail thật',
    en: 'Preview on real Gmail',
  },
  'rules.preview.testingEnabledCount': {
    vi: 'Đang test {count} quy tắc đang bật',
    en: 'Testing {count} enabled rules',
  },
  'rules.preview.empty.heading': {
    vi: 'Chưa chạy thử',
    en: 'No preview run yet',
  },
  'rules.preview.empty.body': {
    vi: 'Chọn một quy tắc ở trên rồi chạy thử với email Gmail gần đây trước khi bật.',
    en: 'Select a rule above, then test it against recent Gmail messages before enabling it.',
  },
  'rules.preview.noWriteNotice': {
    vi: 'Không có thay đổi nào được áp dụng lên Gmail.',
    en: 'No Gmail changes were made.',
  },
  'rules.preview.sampleSize': {
    vi: 'Cỡ mẫu',
    en: 'Sample size',
  },
  'rules.preview.sampled': {
    vi: '{count} thư đã lấy mẫu',
    en: '{count} sampled',
  },
  'rules.preview.matched': {
    vi: '{count} khớp',
    en: '{count} matched',
  },
  'rules.preview.deferred': {
    vi: '{count} kiểm tra trì hoãn',
    en: '{count} deferred',
  },
  'rules.preview.conflicts': {
    vi: '{count} xung đột',
    en: '{count} conflicts',
  },
  'rules.preview.previewCta': {
    vi: 'Chạy thử quy tắc',
    en: 'Preview rule',
  },
  'rules.preview.previewing': {
    vi: 'Đang chạy thử...',
    en: 'Previewing...',
  },
  'rules.preview.enableCta': {
    vi: 'Bật quy tắc',
    en: 'Enable rule',
  },
  'rules.preview.disableCta': {
    vi: 'Tắt quy tắc',
    en: 'Disable rule',
  },
  'rules.preview.deferredSemantic': {
    vi: 'Kiểm tra ngữ nghĩa bị trì hoãn',
    en: 'Deferred semantic check',
  },
  'rules.preview.deferredTooltip': {
    vi: 'Phase 03 lưu kiểm tra này, nhưng việc đánh giá được trì hoãn.',
    en: 'Phase 03 stores this check, but evaluation is deferred.',
  },
  'rules.preview.llmCtaTitle': {
    vi: '{count} email cần LLM xác nhận',
    en: '{count} emails need LLM confirmation',
  },
  'rules.preview.llmCtaBody': {
    vi: 'Các quy tắc có matcher ngữ nghĩa hiện đang để "trì hoãn". Chạy LLM để biết chắc rule có match hay không (~{credits} credit).',
    en: 'Rules with semantic matchers are currently deferred. Run the LLM to confirm whether they match (~{credits} credits).',
  },
  'rules.preview.llmCta': {
    vi: 'Chạy LLM xác nhận (~{credits} credit)',
    en: 'Run LLM confirmation (~{credits} credits)',
  },
  'rules.preview.llmRunning': {
    vi: 'Đang gọi LLM...',
    en: 'Calling LLM...',
  },
  'rules.preview.conflictWarning': {
    vi: 'Lượt chạy thử này có xung đột hành động. Hãy xem các quy tắc khớp trước khi bật.',
    en: 'This preview found action conflicts. Review the matched rules before enabling.',
  },
  'rules.preview.gmailLabels': {
    vi: 'Nhãn Gmail',
    en: 'Gmail labels',
  },
  'rules.preview.proposedActions': {
    vi: 'Hành động dự kiến',
    en: 'Proposed actions',
  },
  'rules.preview.evidence': {
    vi: 'Bằng chứng khớp',
    en: 'Evidence',
  },
  'rules.preview.stat.sampled': {
    vi: 'Đã lấy mẫu',
    en: 'Sampled',
  },
  'rules.preview.stat.matched': {
    vi: 'Đã khớp',
    en: 'Matched',
  },
  'rules.preview.stat.deferred': {
    vi: 'Trì hoãn',
    en: 'Deferred',
  },
  'rules.preview.stat.conflicts': {
    vi: 'Xung đột',
    en: 'Conflicts',
  },
  'rules.templates.title': {
    vi: 'Mẫu khởi đầu',
    en: 'Starter templates',
  },
  'rules.templates.useStarter': {
    vi: 'Dùng mẫu này',
    en: 'Use starter rule',
  },
  'rules.templates.materialized': {
    vi: 'Đã tạo',
    en: 'Materialized',
  },
  'rules.templates.disabledByDefault': {
    vi: 'Lưu ở trạng thái tắt cho đến khi chạy thử',
    en: 'Saved disabled until previewed',
  },
  'rules.templates.browseCta': {
    vi: 'Xem mẫu có sẵn',
    en: 'Browse templates',
  },
  'rules.actions.title': {
    vi: 'Hành động có thể dùng',
    en: 'Available actions',
  },
  'rules.actions.available': {
    vi: 'Đang dùng được',
    en: 'Available',
  },
  'rules.actions.unavailableReason': {
    vi: 'Trạng thái: {status}',
    en: 'Status: {status}',
  },
  'rules.actions.willAutoSend': {
    vi: 'Sẽ tự động gửi',
    en: 'Will auto-send',
  },
  'rules.actions.saveDraftInstead': {
    vi: 'Sẽ lưu bản nháp',
    en: 'Will save draft',
  },
  'rules.actions.autoSendChecking': {
    vi: 'Đang kiểm tra',
    en: 'Checking',
  },
  'rules.actions.risk.low': {
    vi: 'Rủi ro thấp',
    en: 'Low risk',
  },
  'rules.actions.risk.medium': {
    vi: 'Rủi ro vừa',
    en: 'Medium risk',
  },
  'rules.actions.risk.high': {
    vi: 'Rủi ro cao',
    en: 'High risk',
  },
  'rules.actions.empty': {
    vi: 'Chưa có hành động nào đang bật trong catalog.',
    en: 'No enabled catalog actions yet.',
  },
  'rules.actions.error': {
    vi: 'Chưa tải được danh sách hành động.',
    en: 'Could not load available actions.',
  },
  'rules.settings.autoSend.title': {
    vi: 'Auto-send rules',
    en: 'Auto-send rules',
  },
  'rules.settings.autoSend.bodyOn': {
    vi: 'Quy tắc outbound có thể gửi thật khi các cổng an toàn pass.',
    en: 'Outbound rules can send when safety gates pass.',
  },
  'rules.settings.autoSend.bodyOff': {
    vi: 'Quy tắc outbound vẫn lưu được, nhưng runtime sẽ lưu bản nháp Gmail thay vì gửi.',
    en: 'Outbound rules still save, but runtime saves Gmail drafts instead of sending.',
  },
  'rules.settings.autoSend.toggleLabel': {
    vi: 'Cho phép rule tự gửi',
    en: 'Allow rules to auto-send',
  },
  'rules.settings.autoSend.footerOn': {
    vi: 'Bạn vẫn thấy trạng thái "Sẽ tự động gửi" trong rule/action trước khi bật rule.',
    en: 'You still see "Will auto-send" status in rule/action surfaces before enabling a rule.',
  },
  'rules.settings.autoSend.footerOff': {
    vi: 'Tắt switch này không chặn lưu rule; nó đổi outbound runtime sang lưu bản nháp.',
    en: 'Turning this off does not block saving rules; it changes outbound runtime to draft fallback.',
  },
  'settings.privacy.outboundControl': {
    vi: 'Hành động gửi outbound được kiểm soát bằng cổng an toàn và switch Auto-send rules.',
    en: 'Outbound sends are controlled by safety gates and the Auto-send rules switch.',
  },
  'settings.privacy.noAutoSend': {
    vi: 'Hành động gửi outbound được kiểm soát bằng cổng an toàn và switch Auto-send rules.',
    en: 'Outbound sends are controlled by safety gates and the Auto-send rules switch.',
  },
  'rules.delete.title': {
    vi: 'Xóa quy tắc này?',
    en: 'Delete this rule?',
  },
  'rules.delete.body': {
    vi: 'Thao tác này xóa định nghĩa quy tắc. Gmail sẽ không thay đổi, nhưng quy tắc này sẽ không còn xuất hiện trong các lượt chạy thử.',
    en: 'This removes the rule definition. Gmail will not be changed, but this rule will no longer appear in previews.',
  },
  'rules.delete.confirm': {
    vi: 'Xóa quy tắc',
    en: 'Delete rule',
  },
  'rules.delete.dismiss': {
    vi: 'Giữ quy tắc',
    en: 'Keep rule',
  },
  'rules.testCustom.openCta': {
    vi: 'Thử với email tùy chỉnh',
    en: 'Test with custom email',
  },
  'rules.testCustom.title': {
    vi: 'Thử với email tùy chỉnh',
    en: 'Test with a custom email',
  },
  'rules.testCustom.intro': {
    vi: 'Soạn email giả lập để xem quy tắc nào sẽ khớp. Email này KHÔNG được gửi và Gmail không bị thay đổi.',
    en: 'Compose a hypothetical email to see which rules would match. The email is never sent and Gmail is not changed.',
  },
  'rules.testCustom.subjectLabel': {
    vi: 'Tiêu đề',
    en: 'Subject',
  },
  'rules.testCustom.subjectPlaceholder': {
    vi: 'Ví dụ: Hóa đơn Stripe tháng 5 — $49',
    en: 'Example: Stripe receipt May — $49',
  },
  'rules.testCustom.bodyLabel': {
    vi: 'Nội dung email',
    en: 'Email body',
  },
  'rules.testCustom.bodyPlaceholder': {
    vi: 'Dán hoặc gõ nội dung email tại đây. Ví dụ: Thank you for your payment of $49. To unsubscribe, click here.',
    en: 'Paste or type email content here. Example: Thank you for your payment of $49. To unsubscribe, click here.',
  },
  'rules.testCustom.runCta': {
    vi: 'Chạy thử trên email này',
    en: 'Test on this email',
  },
  'rules.testCustom.running': {
    vi: 'Đang chạy thử...',
    en: 'Running...',
  },
  'rules.testCustom.selectedHint': {
    vi: '{count} quy tắc đã chọn — sẽ chỉ thử các quy tắc này.',
    en: '{count} rules selected — only these will be tested.',
  },
  'rules.testCustom.noSelectedHint': {
    vi: 'Chưa chọn quy tắc nào — sẽ thử tất cả quy tắc đang bật.',
    en: 'No rules selected — all enabled rules will be tested.',
  },
  'rules.testCustom.clearSelection': {
    vi: 'Bỏ chọn',
    en: 'Clear selection',
  },
  'rules.testCustom.empty.heading': {
    vi: 'Chưa có kết quả',
    en: 'No results yet',
  },
  'rules.testCustom.empty.body': {
    vi: 'Nhập tiêu đề và nội dung email, sau đó bấm "Chạy thử" để xem quy tắc nào sẽ khớp.',
    en: 'Enter a subject and body, then click "Test" to see which rules would match.',
  },
  'rules.testCustom.result.title': {
    vi: 'Kết quả thử ({count} quy tắc)',
    en: 'Test results ({count} rules)',
  },
  'rules.testCustom.result.matched': {
    vi: 'Khớp',
    en: 'Matched',
  },
  'rules.testCustom.result.deferred': {
    vi: 'Cần kiểm tra ngữ nghĩa',
    en: 'Needs semantic check',
  },
  'rules.testCustom.result.notMatched': {
    vi: 'Không khớp',
    en: 'No match',
  },
  'rules.testCustom.result.disabled': {
    vi: 'Đang tắt',
    en: 'Disabled',
  },
  'rules.testCustom.result.actions': {
    vi: 'Hành động sẽ chạy',
    en: 'Actions that would fire',
  },
  'rules.testCustom.result.evidence': {
    vi: 'Bằng chứng khớp',
    en: 'Match evidence',
  },
  'rules.testCustom.result.deferredEvidence': {
    vi: 'Kiểm tra trì hoãn',
    en: 'Deferred checks',
  },
  'rules.testCustom.result.noMatchHint': {
    vi: 'Quy tắc này không khớp với email tùy chỉnh — kiểm tra điều kiện hoặc thử nội dung khác.',
    en: 'This rule did not match the custom email — review the condition or try different content.',
  },
  'errors.rules.testCustom.generic': {
    vi: 'Không thể chạy thử lúc này. Hãy thử lại.',
    en: 'Could not run the custom-mail test. Please try again.',
  },
  'errors.rules.testCustom.subjectOrBodyRequired': {
    vi: 'Hãy nhập ít nhất tiêu đề hoặc nội dung email.',
    en: 'Enter at least a subject or a body.',
  },
  'errors.rules.compile.invalid': {
    vi: 'Zero Mail chưa kiểm tra được quy tắc này. Hãy sửa cách diễn đạt hoặc trả lời câu hỏi làm rõ rồi kiểm tra lại.',
    en: 'Zero Mail could not review this rule. Edit the wording or answer the clarification, then review again.',
  },
  'errors.rules.refine.generic': {
    vi: 'Chưa áp dụng được chỉnh sửa này. Bản nháp hiện tại được giữ nguyên — hãy mô tả lại yêu cầu chỉnh sửa rõ hơn rồi thử lại.',
    en: 'Could not apply this edit. Your current draft is preserved — rephrase the edit request and try again.',
  },
  'errors.rules.compile.clarification_required': {
    vi: 'Quy tắc này cần một câu trả lời làm rõ trước khi có thể lưu.',
    en: 'This rule needs one clarification answer before it can be saved.',
  },
  'errors.rules.preview.required': {
    vi: 'Hãy chạy thử phiên bản quy tắc hiện tại trước khi bật.',
    en: 'Preview the current rule version before enabling it.',
  },
  'errors.rules.preview.invalid_sample_size': {
    vi: 'Cỡ mẫu chạy thử phải là 10, 25 hoặc 50.',
    en: 'Preview sample size must be 10, 25, or 50.',
  },
  'errors.rules.preview.generic': {
    vi: 'Không thể chạy thử. Kiểm tra kết nối Gmail và tín dụng, rồi chạy thử lại.',
    en: 'Preview could not finish. Check Gmail connection and credits, then preview the rule again.',
  },
  'errors.rules.save.generic': {
    vi: 'Không thể lưu quy tắc lúc này. Hãy tải lại danh sách rồi thử lại.',
    en: 'The rule could not be saved right now. Reload the list and try again.',
  },
  'errors.rules.duplicate': {
    vi: 'Quy tắc giống hệt đã tồn tại trong danh sách. Hãy sửa quy tắc cũ hoặc thay đổi điều kiện/hành động.',
    en: 'An identical rule already exists in the list. Edit the existing rule or change the conditions/actions.',
  },
  'errors.rules.insufficientCredits': {
    vi: 'Tín dụng nền tảng đã hết. Hãy nạp thêm tín dụng hoặc lưu khóa BYOK hợp lệ trước khi kiểm tra hoặc chạy thử quy tắc.',
    en: 'Platform credits are depleted. Top up credits or save a valid BYOK key before reviewing or previewing rules.',
  },
  'errors.rules.gmail.unavailable': {
    vi: 'Không thể chạy thử qua Gmail lúc này. Kết nối lại Gmail rồi thử lại.',
    en: 'Gmail preview is unavailable. Reconnect Gmail and try again.',
  },
  'errors.rules.not_found': {
    vi: 'Không tìm thấy quy tắc này. Hãy tải lại danh sách quy tắc.',
    en: 'This rule could not be found. Reload the rules list.',
  },
  'errors.rules.reorder.invalid': {
    vi: 'Không thể lưu thứ tự quy tắc. Hãy tải lại danh sách rồi thử lại.',
    en: 'Could not save rule order. Reload the list and try again.',
  },
  'errors.rules.version_mismatch': {
    vi: 'Quy tắc đã thay đổi trước khi thao tác hoàn tất. Tải lại rồi thử lại.',
    en: 'The rule changed before the action finished. Reload and try again.',
  },
  'errors.rules.unsafe_action': {
    vi: 'Quy tắc này yêu cầu hành động không được phép trong v1.',
    en: 'This rule asks for an action that is not allowed in v1.',
  },
} as const;
