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
  'rules.testGmail.reload': {
    vi: 'Tải lại',
    en: 'Reload',
  },
  'rules.testGmail.testAll': {
    vi: 'Test tất cả',
    en: 'Test all',
  },
  'rules.testGmail.stop': {
    vi: 'Dừng',
    en: 'Stop',
  },
  'rules.testGmail.testRow': {
    vi: 'Test',
    en: 'Test',
  },
  'rules.testGmail.retestRow': {
    vi: 'Test lại',
    en: 'Retest',
  },
  'rules.testGmail.empty.heading': {
    vi: 'Không có email gần đây',
    en: 'No recent emails',
  },
  'rules.testGmail.empty.body': {
    vi: 'Không tải được email Gmail gần đây. Bấm "Tải lại" để thử lại.',
    en: 'Could not load recent Gmail emails. Click "Reload" to try again.',
  },
  'rules.tabs.gmailCreditWarningTitle': {
    vi: 'Test trên Gmail thật sẽ tốn credit',
    en: 'Real Gmail test uses credits',
  },
  'rules.tabs.gmailCreditWarningBody': {
    vi: 'Mỗi lần chạy thử sẽ lấy mẫu email Gmail gần đây và gọi LLM cho các matcher ngữ nghĩa, nên tốn credit nền tảng. Gmail của bạn không bị thay đổi.',
    en: 'Each run samples your recent Gmail and calls the LLM for semantic matchers, so it consumes platform credits. Your Gmail is not modified.',
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
  'rules.copy.button': {
    vi: 'Sao chép quy tắc',
    en: 'Copy rules',
  },
  'rules.copy.title': {
    vi: 'Sao chép quy tắc từ hộp thư khác',
    en: 'Copy rules from another mailbox',
  },
  'rules.copy.body': {
    vi: 'Quy tắc được sao chép vào hộp thư đang dùng và luôn ở trạng thái tắt để bạn kiểm tra trước.',
    en: 'Copied rules are added to the active mailbox and stay disabled until you review them.',
  },
  'rules.copy.sourceLabel': {
    vi: 'Hộp thư nguồn',
    en: 'Source mailbox',
  },
  'rules.copy.sourcePlaceholder': {
    vi: 'Chọn hộp thư',
    en: 'Choose mailbox',
  },
  'rules.copy.activeMailbox': {
    vi: 'Hộp thư đích đang dùng',
    en: 'Active target mailbox',
  },
  'rules.copy.empty': {
    vi: 'Kết nối thêm một Gmail để sao chép quy tắc giữa các hộp thư.',
    en: 'Connect another Gmail account to copy rules between mailboxes.',
  },
  'rules.copy.cancel': {
    vi: 'Hủy',
    en: 'Cancel',
  },
  'rules.copy.confirm': {
    vi: 'Sao chép',
    en: 'Copy',
  },
  'rules.copy.success': {
    vi: 'Đã sao chép quy tắc',
    en: 'Rules copied',
  },
  'rules.copy.copied': {
    vi: 'Đã sao chép {count} quy tắc',
    en: 'Copied {count} rules',
  },
  'errors.rules.copy.generic': {
    vi: 'Không thể sao chép quy tắc lúc này. Hãy tải lại rồi thử lại.',
    en: 'Rules could not be copied right now. Reload and try again.',
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
    vi: 'Lưu',
    en: 'Save',
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
    vi: 'Quy tắc được lưu thành điều kiện và hành động rõ ràng. Hành động gửi email chỉ chạy khi qua kiểm tra an toàn và cài đặt tự gửi của bạn.',
    en: 'Rules are saved as clear criteria and actions. Email-sending actions run only when safety checks and your auto-send setting allow them.',
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
    vi: 'Dùng ví dụ mẫu nếu bạn chưa muốn tự viết từ đầu.',
    en: 'Use a starter example if you do not want to write from scratch.',
  },
  'rules.composer.examples.choosePersonaTitle': {
    vi: 'Chọn persona',
    en: 'Choose persona',
  },
  'rules.composer.examples.chooseExampleTitle': {
    vi: 'Chọn ví dụ',
    en: 'Choose examples',
  },
  'rules.composer.examples.backToPersonas': {
    vi: 'Bỏ chọn persona',
    en: 'Clear persona',
  },
  'rules.composer.examples.changePersona': {
    vi: 'Đổi persona',
    en: 'Change persona',
  },
  'rules.composer.examples.count': {
    vi: '{count} ví dụ',
    en: '{count} examples',
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
    vi: 'Chọn Zero Mail sẽ làm gì khi email khớp. Nếu là gửi email, hệ thống vẫn kiểm tra an toàn trước khi gửi.',
    en: 'Choose what Zero Mail should do when an email matches. If the action sends email, safety checks still run before sending.',
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
    vi: 'Cách AI gửi email',
    en: 'How AI sends email',
  },
  'rules.manual.outbound.autoSend': {
    vi: 'Có thể gửi tự động khi an toàn',
    en: 'Can send automatically when safe',
  },
  'rules.manual.outbound.fallbackDraft': {
    vi: 'Nếu chưa đủ an toàn, lưu nháp Gmail',
    en: 'If not safe enough, save a Gmail draft',
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
  'rules.preview.applyLabelsCta': {
    vi: 'Áp dụng nhãn',
    en: 'Apply labels',
  },
  'rules.preview.applyingLabels': {
    vi: 'Đang gắn nhãn...',
    en: 'Applying labels...',
  },
  'rules.preview.labelsApplied': {
    vi: 'Đã gắn {count} nhãn lên Gmail. Inbox trong hệ thống sẽ hiển thị nhãn này khi email nằm trong 100 email gần nhất.',
    en: 'Applied {count} labels in Gmail. The system inbox shows these labels when the messages are in the 100 most recent emails.',
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
  'rules.actions.title': {
    vi: 'Hành động có thể dùng',
    en: 'Available actions',
  },
  'rules.actions.description': {
    vi: 'Danh sách tham khảo để biết rule có thể yêu cầu AI làm gì.',
    en: 'A quick reference for what a rule can ask the AI to do.',
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
    vi: 'Có thể tự gửi',
    en: 'Can auto-send',
  },
  'rules.actions.saveDraftInstead': {
    vi: 'Sẽ lưu nháp',
    en: 'Saves draft',
  },
  'rules.actions.autoSendAllowed': {
    vi: 'Có thể tự gửi',
    en: 'Can auto-send',
  },
  'rules.actions.draftFallback': {
    vi: 'Sẽ lưu nháp',
    en: 'Saves draft',
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
    vi: 'Tự gửi email bằng rule',
    en: 'Rule auto-send',
  },
  'rules.settings.autoSend.bodyOn': {
    vi: 'Khi rule yêu cầu gửi trả lời, chuyển tiếp, hoặc gửi email mới, Zero Mail có thể gửi thật nếu qua kiểm tra an toàn.',
    en: 'When a rule asks to reply, forward, or send a new email, Zero Mail can send it if safety checks pass.',
  },
  'rules.settings.autoSend.bodyOff': {
    vi: 'Rule vẫn lưu được, nhưng các hành động gửi email sẽ tạo bản nháp Gmail để bạn tự kiểm tra và gửi.',
    en: 'Rules still save, but email-sending actions create Gmail drafts for you to review and send.',
  },
  'rules.settings.autoSend.toggleLabel': {
    vi: 'Cho phép rule tự gửi email',
    en: 'Allow rules to send email',
  },
  'rules.settings.autoSend.footerOn': {
    vi: 'Các rule gửi email vẫn phải qua kiểm tra an toàn, giới hạn số lần gửi và nhật ký audit.',
    en: 'Email-sending rules still pass safety checks, send limits, and audit logging.',
  },
  'rules.settings.autoSend.footerOff': {
    vi: 'Tắt switch này không chặn lưu rule; nó chỉ đổi hành động gửi email thành bản nháp Gmail.',
    en: 'Turning this off does not block saving rules; it changes email-sending actions to Gmail drafts.',
  },
  'settings.privacy.outboundControl': {
    vi: 'Hành động gửi email được kiểm soát bằng kiểm tra an toàn và switch tự gửi trong Cấu hình AI.',
    en: 'Email-sending actions are controlled by safety checks and the auto-send switch in AI configuration.',
  },
  'settings.privacy.noAutoSend': {
    vi: 'Hành động gửi email được kiểm soát bằng kiểm tra an toàn và switch tự gửi trong Cấu hình AI.',
    en: 'Email-sending actions are controlled by safety checks and the auto-send switch in AI configuration.',
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
    vi: 'Soạn email giả lập để xem quy tắc nào sẽ khớp. Email không được gửi và Gmail không bị thay đổi, nhưng quy tắc có matcher ngữ nghĩa sẽ gọi LLM nên mỗi lần chạy tốn credit.',
    en: 'Compose a hypothetical email to see which rules would match. The email is never sent and Gmail is not changed, but rules with semantic matchers call the LLM, so each run uses credits.',
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
    vi: 'Cỡ mẫu chạy thử phải là 10, 20, 50 hoặc 100.',
    en: 'Preview sample size must be 10, 20, 50, or 100.',
  },
  'errors.rules.preview.generic': {
    vi: 'Không thể chạy thử do lỗi máy chủ. Vui lòng thử lại sau giây lát.',
    en: 'Preview could not finish due to a server error. Please try again in a moment.',
  },
  'errors.rules.testGmail.generic': {
    vi: 'Không thể test email này lúc này. Vui lòng thử lại sau giây lát.',
    en: 'Could not test this email right now. Please try again in a moment.',
  },
  'errors.rules.applyLabels.generic': {
    vi: 'Không thể gắn nhãn lên Gmail lúc này. Kiểm tra kết nối Gmail rồi thử lại.',
    en: 'Could not apply labels in Gmail right now. Check the Gmail connection and try again.',
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
