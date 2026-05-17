export const triageMessages = {
  'triage.page.title': {
    vi: 'AI xử lý email',
    en: 'AI email actions',
  },
  'triage.page.description': {
    vi: 'Xem quy tắc nào đã khớp, Zero Mail đã làm gì với Gmail, vì sao hành động đó xảy ra, và hoàn tác trong 30 ngày khi cần.',
    en: 'See which rule matched, what Zero Mail changed in Gmail, why it happened, and undo within 30 days when needed.',
  },
  'triage.tabs.label': {
    vi: 'Khu vực xử lý email',
    en: 'Email action sections',
  },
  'triage.tabs.audit': {
    vi: 'Hoạt động',
    en: 'Activity',
  },
  'triage.tabs.senders': {
    vi: 'Người gửi quan trọng',
    en: 'Protected senders',
  },
  'triage.flow.observed': {
    vi: 'Gmail mới đến',
    en: 'New Gmail arrives',
  },
  'triage.flow.matched': {
    vi: 'Khớp quy tắc When',
    en: 'Matches a When rule',
  },
  'triage.flow.applied': {
    vi: 'Chỉ làm action cho phép',
    en: 'Applies allowed action',
  },
  'triage.flow.undo': {
    vi: 'Có thể hoàn tác 30 ngày',
    en: 'Undo available 30 days',
  },
  'triage.audit.empty.title': {
    vi: 'Chưa có hành động email',
    en: 'No email actions yet',
  },
  'triage.audit.empty.body': {
    vi: 'Khi một quy tắc khớp email, bạn sẽ thấy When, Then, lý do và nút hoàn tác tại đây.',
    en: 'When a rule matches an email, you will see the When, Then, reason, and undo action here.',
  },
  'triage.audit.error.title': {
    vi: 'Không tải được nhật ký triage',
    en: 'Could not load triage history',
  },
  'triage.audit.error.body': {
    vi: 'Hãy thử tải lại danh sách trước khi quyết định hoàn tác.',
    en: 'Retry the list before deciding whether to undo an action.',
  },
  'triage.audit.error.retry': {
    vi: 'Tải lại',
    en: 'Retry',
  },
  'triage.audit.loadOlder': {
    vi: 'Tải mục cũ hơn',
    en: 'Load older entries',
  },
  'triage.audit.loadMore': {
    vi: 'Tải thêm',
    en: 'Load more',
  },
  'triage.audit.endOfList': {
    vi: 'Đã hết danh sách.',
    en: "That's everything.",
  },
  'triage.audit.loadingOlder': {
    vi: 'Đang tải...',
    en: 'Loading...',
  },
  'triage.audit.loadingMore': {
    vi: 'Đang tải...',
    en: 'Loading...',
  },
  'triage.audit.draftAction': {
    vi: 'Soạn nháp trả lời',
    en: 'Draft reply',
  },
  'triage.audit.regenerateDraftAction': {
    vi: 'Tạo lại bản nháp',
    en: 'Regenerate draft',
  },
  'triage.audit.boundary': {
    vi: 'Cũ hơn 30 ngày - không thể hoàn tác',
    en: 'Older than 30 days - undo no longer available',
  },
  'triage.audit.columns.timestamp': {
    vi: 'Thời gian',
    en: 'Time',
  },
  'triage.audit.columns.message': {
    vi: 'Email',
    en: 'Message',
  },
  'triage.audit.columns.rule': {
    vi: 'Quy tắc',
    en: 'Rule',
  },
  'triage.audit.columns.action': {
    vi: 'Hành động',
    en: 'Action',
  },
  'triage.audit.columns.reason': {
    vi: 'Lý do',
    en: 'Reason',
  },
  'triage.audit.columns.undo': {
    vi: 'Hoàn tác',
    en: 'Undo',
  },
  'triage.audit.columns.actions': {
    vi: 'Hành động',
    en: 'Actions',
  },
  'triage.audit.whenLabel': {
    vi: 'Khi',
    en: 'When',
  },
  'triage.audit.thenLabel': {
    vi: 'Thì',
    en: 'Then',
  },
  'triage.audit.whyLabel': {
    vi: 'Vì sao',
    en: 'Why',
  },
  'triage.audit.noReason': {
    vi: 'Không có lý do chi tiết',
    en: 'No detailed reason',
  },
  'triage.audit.message.untitled': {
    vi: 'Email không tiêu đề',
    en: 'Untitled message',
  },
  'triage.audit.message.unknownSender': {
    vi: 'Người gửi không rõ',
    en: 'Unknown sender',
  },
  'triage.audit.undo.cta': {
    vi: 'Hoàn tác',
    en: 'Undo',
  },
  'triage.audit.undo.dialogTitle': {
    vi: 'Hoàn tác hành động triage này?',
    en: 'Undo this triage action?',
  },
  'triage.audit.undo.dialogDescription': {
    vi: 'Zero Mail sẽ thực hiện hành động ngược lại: {action}',
    en: 'Zero Mail will apply the inverse Gmail change: {action}',
  },
  'triage.audit.undo.confirm': {
    vi: 'Hoàn tác hành động này',
    en: 'Undo this action',
  },
  'triage.audit.undo.cancel': {
    vi: 'Giữ nguyên',
    en: 'Keep it',
  },
  'triage.audit.undo.success': {
    vi: 'Đã hoàn tác hành động triage.',
    en: 'Triage action undone.',
  },
  'triage.audit.undo.error': {
    vi: 'Chưa thể hoàn tác hành động này.',
    en: 'Could not undo this action.',
  },
  'triage.audit.undo.windowClosed': {
    vi: 'Đã hết hạn hoàn tác',
    en: 'Undo window closed',
  },
  'triage.audit.undo.windowTooltip': {
    vi: 'Hành động triage chỉ có thể hoàn tác trong vòng 30 ngày.',
    en: 'Triage actions can be undone for up to 30 days.',
  },
  'triage.audit.undo.undone': {
    vi: 'Đã hoàn tác',
    en: 'Undone',
  },
  'triage.senders.title': {
    vi: 'Người gửi được bảo vệ',
    en: 'Protected senders',
  },
  'triage.senders.body': {
    vi: 'Những người gửi quan trọng được giữ an toàn cho đến khi bạn cho phép tự động hóa.',
    en: 'Important senders stay protected until you opt them into automation.',
  },
  'triage.senders.empty.title': {
    vi: 'Chưa có người gửi được bảo vệ',
    en: 'No protected senders yet',
  },
  'triage.senders.empty.body': {
    vi: 'Khi Zero Mail phát hiện người gửi quan trọng, họ sẽ xuất hiện tại đây.',
    en: 'When Zero Mail detects important senders, they will appear here.',
  },
  'triage.senders.error.title': {
    vi: 'Không tải được danh sách người gửi',
    en: 'Could not load protected senders',
  },
  'triage.senders.error.body': {
    vi: 'Hãy thử lại trước khi thay đổi tự động hóa cho người gửi.',
    en: 'Retry before changing sender automation.',
  },
  'triage.senders.error.retry': {
    vi: 'Tải lại',
    en: 'Retry',
  },
  'triage.senders.optIn': {
    vi: 'Cho phép tự động hóa',
    en: 'Opt into automation',
  },
  'triage.senders.optedIn': {
    vi: 'Đã cho phép',
    en: 'Opted in',
  },
  'triage.senders.unknown': {
    vi: 'Người gửi không rõ',
    en: 'Unknown sender',
  },
  'errors.triage.undo.already_done': {
    vi: 'Hành động email này đã được hoàn tác hoặc không còn ở trạng thái có thể hoàn tác.',
    en: 'This email action has already been undone or is no longer undoable.',
  },
  'errors.triage.undo.expired': {
    vi: 'Chỉ có thể hoàn tác hành động triage trong vòng 30 ngày sau khi áp dụng.',
    en: 'Triage actions can only be undone within 30 days after they are applied.',
  },
  'errors.triage.undo.unsupported_action': {
    vi: 'Zero Mail không thể hoàn tác loại hành động triage này một cách an toàn.',
    en: 'Zero Mail cannot safely undo this triage action type.',
  },
  'errors.triage.undo.write_failed': {
    vi: 'Chưa thể hoàn tác hành động email lúc này. Hãy thử lại sau.',
    en: 'This email action could not be undone right now. Try again later.',
  },
  'errors.triage.audit.not_found': {
    vi: 'Không tìm thấy bản ghi triage này. Hãy tải lại danh sách.',
    en: 'This triage audit entry could not be found. Reload the list.',
  },
  'errors.triage.safety_violation': {
    vi: 'Hành động triage bị chặn vì vi phạm ràng buộc an toàn.',
    en: 'The triage action was blocked by a safety constraint.',
  },
} as const;
