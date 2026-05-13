export const triageMessages = {
  'triage.page.title': {
    vi: 'Triage',
    en: 'Triage',
  },
  'triage.page.description': {
    vi: 'Kiểm tra những gì AI đã làm, chạy thử shadow mode, và kiểm soát các người gửi được bảo vệ trước khi cho phép tự động hóa.',
    en: 'Review what AI changed, run shadow mode, and manage protected senders before trusting automation.',
  },
  'triage.tabs.label': {
    vi: 'Khu vực triage',
    en: 'Triage sections',
  },
  'triage.tabs.audit': {
    vi: 'Nhật ký',
    en: 'Audit log',
  },
  'triage.tabs.shadow': {
    vi: 'Shadow mode',
    en: 'Shadow mode',
  },
  'triage.tabs.senders': {
    vi: 'Người gửi',
    en: 'Sender safety net',
  },
  'triage.audit.empty.title': {
    vi: 'Chưa có hoạt động triage',
    en: 'No triage activity yet',
  },
  'triage.audit.empty.body': {
    vi: 'Khi Zero Mail xử lý email, các quyết định và lý do sẽ xuất hiện ở đây.',
    en: 'When Zero Mail processes email, decisions and reasons will appear here.',
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
  'triage.shadow.title': {
    vi: 'Shadow mode',
    en: 'Shadow mode',
  },
  'triage.shadow.body': {
    vi: 'Chạy triage như một bản nháp an toàn: Zero Mail ghi nhận quyết định nhưng không thay đổi Gmail.',
    en: 'Run triage as a safe rehearsal: Zero Mail records decisions without changing Gmail.',
  },
  'triage.shadow.toggleLabel': {
    vi: 'Bật shadow mode',
    en: 'Enable shadow mode',
  },
  'triage.shadow.badgeOn': {
    vi: 'Shadow mode đang bật',
    en: 'Shadow mode on',
  },
  'triage.shadow.onBody': {
    vi: 'Zero Mail sẽ đánh giá email nhưng không áp dụng nhãn, lưu draft, hoặc archive.',
    en: 'Zero Mail will evaluate email without applying labels, saving drafts, or archiving.',
  },
  'triage.shadow.offBody': {
    vi: 'Các hành động triage được phép sẽ được áp dụng sau khi qua kiểm tra an toàn.',
    en: 'Allowed triage actions will apply after safety checks.',
  },
  'triage.shadow.confirm.title': {
    vi: 'Tắt shadow mode?',
    en: 'Turn off shadow mode?',
  },
  'triage.shadow.confirm.body': {
    vi: 'Sau khi tắt, Zero Mail có thể áp dụng các hành động Gmail được phép.',
    en: 'After this is off, Zero Mail can apply allowed Gmail actions.',
  },
  'triage.shadow.confirm.action': {
    vi: 'Tắt shadow mode',
    en: 'Turn off shadow mode',
  },
  'triage.shadow.confirm.cancel': {
    vi: 'Giữ shadow mode',
    en: 'Keep shadow mode on',
  },
  'triage.shadow.error.title': {
    vi: 'Không tải được shadow mode',
    en: 'Could not load shadow mode',
  },
  'triage.shadow.error.body': {
    vi: 'Hãy thử lại trước khi thay đổi chế độ chạy triage.',
    en: 'Retry before changing how triage runs.',
  },
  'triage.shadow.error.retry': {
    vi: 'Tải lại',
    en: 'Retry',
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
