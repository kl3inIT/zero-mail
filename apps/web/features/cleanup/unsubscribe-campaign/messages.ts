export const unsubscribeCampaignMessages = {
  // List page
  'cleanup.unsubscribe.list.title': {
    vi: 'Hủy đăng ký',
    en: 'Unsubscribe',
  },
  'cleanup.unsubscribe.list.lead': {
    vi: 'Zero Mail tìm các email có hỗ trợ hủy nhận từ Gmail. Khi bạn xác nhận, hệ thống gửi yêu cầu hủy nhận an toàn; nếu thành công mới lưu trữ email cũ. Không xóa email.',
    en: 'Zero Mail finds senders that support unsubscribe from Gmail. When you confirm, it sends a safe unsubscribe request and archives old mail only after that succeeds. It never deletes mail.',
  },
  'cleanup.unsubscribe.list.suppressionLink': {
    vi: 'Danh sách an toàn',
    en: 'Safe list',
  },
  'cleanup.unsubscribe.list.searchPlaceholder': {
    vi: 'Tìm người gửi hoặc tên miền',
    en: 'Search sender or domain',
  },
  'cleanup.unsubscribe.list.filterLabel': {
    vi: 'Lọc người gửi',
    en: 'Filter senders',
  },
  'cleanup.unsubscribe.list.filter.all': {
    vi: 'Tất cả người gửi',
    en: 'All senders',
  },
  'cleanup.unsubscribe.list.filter.ready': {
    vi: 'Có thể hủy nhận',
    en: 'Ready to process',
  },
  'cleanup.unsubscribe.list.filter.oneClick': {
    vi: 'Hủy nhận an toàn',
    en: 'Safe unsubscribe',
  },
  'cleanup.unsubscribe.list.filter.mailto': {
    vi: 'Gửi email hủy nhận',
    en: 'Unsubscribe by email',
  },
  'cleanup.unsubscribe.list.sortLabel': {
    vi: 'Sắp xếp người gửi',
    en: 'Sort senders',
  },
  'cleanup.unsubscribe.list.sort.count': {
    vi: 'Nhiều email nhất',
    en: 'Most mail',
  },
  'cleanup.unsubscribe.list.sort.recent': {
    vi: 'Mới thấy gần đây',
    en: 'Recently seen',
  },
  'cleanup.unsubscribe.list.sort.sender': {
    vi: 'A-Z theo người gửi',
    en: 'A-Z sender',
  },
  'cleanup.unsubscribe.list.stats.total': {
    vi: 'Người gửi đã phát hiện',
    en: 'Detected senders',
  },
  'cleanup.unsubscribe.list.stats.ready': {
    vi: 'Có thể hủy nhận',
    en: 'Actionable',
  },
  'cleanup.unsubscribe.list.stats.messages': {
    vi: 'Email sẽ lưu trữ',
    en: 'Mail to archive',
  },
  'cleanup.unsubscribe.list.stats.oneClick': {
    vi: 'Hủy nhận an toàn',
    en: 'Safe unsubscribe',
  },
  'cleanup.unsubscribe.list.counter': {
    vi: '{count} / 25 người gửi đã chọn',
    en: '{count} / 25 senders selected',
  },
  'cleanup.unsubscribe.list.selectedMail': {
    vi: '{count} email lịch sử trong lựa chọn',
    en: '{count} history mail in selection',
  },
  'cleanup.unsubscribe.list.counterOver': {
    vi: '{count} / 25 người gửi - vượt giới hạn',
    en: '{count} / 25 senders — over the limit',
  },
  'cleanup.unsubscribe.list.preview': {
    vi: 'Xem trước',
    en: 'Preview',
  },
  'cleanup.unsubscribe.list.clear': {
    vi: 'Bỏ chọn',
    en: 'Clear selection',
  },
  'cleanup.unsubscribe.list.col.sender': {
    vi: 'Người gửi',
    en: 'Sender',
  },
  'cleanup.unsubscribe.list.col.domain': {
    vi: 'Tên miền',
    en: 'Domain',
  },
  'cleanup.unsubscribe.list.col.count': {
    vi: 'Email 30 ngày',
    en: 'Mail (30 days)',
  },
  'cleanup.unsubscribe.list.col.history': {
    vi: 'Lịch sử',
    en: 'History',
  },
  'cleanup.unsubscribe.list.col.risk': {
    vi: 'Trạng thái',
    en: 'Status',
  },
  'cleanup.unsubscribe.list.col.actions': {
    vi: 'Hành động',
    en: 'Actions',
  },
  'cleanup.unsubscribe.list.historyCount': {
    vi: '{count} email',
    en: '{count} mail',
  },
  'cleanup.unsubscribe.list.action.unsubscribe': {
    vi: 'Xem trước',
    en: 'Preview',
  },
  'cleanup.unsubscribe.list.action.keep': {
    vi: 'Thêm vào danh sách an toàn',
    en: 'Add to safe list',
  },
  'cleanup.unsubscribe.list.selectAll': {
    vi: 'Chọn tất cả người gửi đang hiển thị',
    en: 'Select all visible senders',
  },
  'cleanup.unsubscribe.list.action.details': {
    vi: 'Mở chi tiết người gửi',
    en: 'Open sender details',
  },
  'cleanup.unsubscribe.list.action.collapse': {
    vi: 'Đóng chi tiết người gửi',
    en: 'Close sender details',
  },
  'cleanup.unsubscribe.list.detail.lastSeen': {
    vi: 'Lần thấy gần nhất',
    en: 'Last seen',
  },
  'cleanup.unsubscribe.list.detail.archive': {
    vi: 'Email sẽ xử lý',
    en: 'Mail to process',
  },
  'cleanup.unsubscribe.list.detail.archiveValue': {
    vi: 'Nếu hủy nhận thành công, Zero Mail sẽ lưu trữ {count} email gần đây.',
    en: 'If unsubscribe succeeds, Zero Mail will archive {count} recent mail.',
  },
  'cleanup.unsubscribe.list.detail.safety': {
    vi: 'An toàn',
    en: 'Safety',
  },
  'cleanup.unsubscribe.list.detail.safe': {
    vi: 'Không xóa email. Có thể hoàn tác lưu trữ trong 30 ngày.',
    en: 'Does not delete mail. Archive can be undone for 30 days.',
  },
  'cleanup.unsubscribe.list.detail.disabled': {
    vi: 'Gmail chưa cung cấp cách hủy nhận an toàn cho sender này.',
    en: 'Gmail has not provided a safe unsubscribe method for this sender.',
  },
  'cleanup.unsubscribe.list.detail.skip': {
    vi: 'Đưa vào danh sách an toàn',
    en: 'Add to safe list',
  },
  'cleanup.unsubscribe.list.detail.skipDescription': {
    vi: 'Ẩn khỏi các gợi ý hủy đăng ký sau này. Gmail không bị thay đổi.',
    en: 'Hide it from future unsubscribe suggestions. Gmail is not changed.',
  },
  'cleanup.unsubscribe.method.oneClick': {
    vi: 'Hủy nhận an toàn',
    en: 'Safe unsubscribe',
  },
  'cleanup.unsubscribe.method.oneClickTooltip': {
    vi: 'Dùng header Gmail chuẩn RFC 8058. Zero Mail gửi POST an toàn tới endpoint hủy nhận; không mở link trong nội dung email.',
    en: 'Uses the standard Gmail RFC 8058 header. Zero Mail sends a safe POST to the unsubscribe endpoint and does not open links from the email body.',
  },
  'cleanup.unsubscribe.method.mailto': {
    vi: 'Gửi email hủy nhận',
    en: 'Unsubscribe by email',
  },
  'cleanup.unsubscribe.method.mailtoTooltip': {
    vi: 'Gửi một email hủy nhận tới địa chỉ được khai báo trong header Gmail.',
    en: 'Sends an unsubscribe email to the address declared in the Gmail header.',
  },
  'cleanup.unsubscribe.method.none': {
    vi: 'Không hỗ trợ',
    en: 'Unsupported',
  },
  'cleanup.unsubscribe.risk.safe': {
    vi: 'Có thể hủy nhận',
    en: 'Ready',
  },
  'cleanup.unsubscribe.risk.noHeader': {
    vi: 'Không tự động',
    en: 'Not supported',
  },
  'cleanup.unsubscribe.risk.noHeaderTooltip': {
    vi: 'Gmail không có header hủy nhận an toàn cho người gửi này.',
    en: 'Gmail has no safe unsubscribe header for this sender.',
  },
  'cleanup.unsubscribe.risk.suppressed': {
    vi: 'An toàn',
    en: 'Safe-listed',
  },
  'cleanup.unsubscribe.list.empty.title': {
    vi: 'Chưa tìm thấy người gửi có thể hủy đăng ký',
    en: 'No unsubscribe-ready senders found',
  },
  'cleanup.unsubscribe.list.empty.body': {
    vi: 'Zero Mail kiểm tra 100 email Inbox gần nhất và chỉ hiện người gửi có cách hủy nhận an toàn.',
    en: 'Zero Mail checks the 100 most recent Inbox emails and only shows senders with a safe unsubscribe method.',
  },
  'cleanup.unsubscribe.list.empty.link': {
    vi: 'Tìm hiểu cách Zero Mail phát hiện bản tin →',
    en: 'Learn how Zero Mail detects newsletters →',
  },
  'cleanup.unsubscribe.list.error': {
    vi: 'Không tải được danh sách người gửi. Hãy thử lại sau một chút.',
    en: 'Could not load the sender list. Please try again shortly.',
  },
  'cleanup.unsubscribe.list.retry': {
    vi: 'Thử lại',
    en: 'Retry',
  },

  // Preview dialog
  'cleanup.unsubscribe.preview.title': {
    vi: 'Xem trước hủy đăng ký',
    en: 'Preview unsubscribe',
  },
  'cleanup.unsubscribe.preview.description': {
    vi: 'Kiểm tra trước khi xác nhận. Zero Mail chỉ lưu trữ email cũ sau khi hủy nhận thành công. Không xóa email và có thể hoàn tác lưu trữ trong 30 ngày.',
    en: 'Review before confirming. Zero Mail archives old mail only after unsubscribe succeeds. It never deletes mail, and archive can be undone for 30 days.',
  },
  'cleanup.unsubscribe.preview.totalSender': {
    vi: 'Tổng người gửi: {count}',
    en: 'Total senders: {count}',
  },
  'cleanup.unsubscribe.preview.totalMail': {
    vi: 'Email sẽ lưu trữ nếu thành công: {count}',
    en: 'Mail to archive if successful: {count}',
  },
  'cleanup.unsubscribe.preview.capSender': {
    vi: 'Đã vượt giới hạn 25 người gửi. Quay lại và bỏ chọn bớt.',
    en: 'Sender limit of 25 exceeded. Go back and deselect some.',
  },
  'cleanup.unsubscribe.preview.capMessage': {
    vi: 'Đã vượt giới hạn 2.000 email lịch sử ({count}). Quay lại và bỏ chọn bớt.',
    en: 'History mail limit of 2,000 exceeded ({count}). Go back and deselect some.',
  },
  'cleanup.unsubscribe.preview.cancel': {
    vi: 'Quay lại',
    en: 'Back',
  },
  'cleanup.unsubscribe.preview.confirm': {
    vi: 'Hủy đăng ký',
    en: 'Unsubscribe',
  },
  'cleanup.unsubscribe.preview.submitting': {
    vi: 'Đang tạo...',
    en: 'Creating…',
  },
  'cleanup.unsubscribe.preview.empty': {
    vi: 'Không có người gửi nào đủ điều kiện hủy đăng ký trong lựa chọn hiện tại.',
    en: 'No selected sender is eligible for unsubscribe.',
  },
  'cleanup.unsubscribe.preview.willArchive': {
    vi: '{count} email gần đây sẽ được lưu trữ nếu hủy nhận thành công',
    en: '{count} recent mail will be archived if unsubscribe succeeds',
  },
  'cleanup.unsubscribe.preview.willNotArchive': {
    vi: 'Không tự động xử lý sender này',
    en: 'This sender will not be processed automatically',
  },
  'cleanup.unsubscribe.preview.submitOk': {
    vi: 'Đã tạo tác vụ hủy đăng ký. Đang theo dõi tiến độ...',
    en: 'Unsubscribe task created. Tracking progress…',
  },
  'cleanup.unsubscribe.preview.errCapSender': {
    vi: 'Vượt giới hạn 25 người gửi. Hãy bỏ chọn bớt.',
    en: 'Sender limit of 25 exceeded. Please deselect some.',
  },
  'cleanup.unsubscribe.preview.errCapMessage': {
    vi: 'Vượt giới hạn 2.000 email lịch sử. Hãy bỏ chọn bớt.',
    en: 'History mail limit of 2,000 exceeded. Please deselect some.',
  },
  'cleanup.unsubscribe.preview.errGeneric': {
    vi: 'Không tạo được tác vụ hủy đăng ký. Hãy thử lại sau một chút.',
    en: 'Could not create the unsubscribe task. Please try again shortly.',
  },

  // Status page
  'cleanup.unsubscribe.status.title': {
    vi: 'Hủy đăng ký #{shortId}',
    en: 'Unsubscribe #{shortId}',
  },
  'cleanup.unsubscribe.status.breadcrumb': {
    vi: 'Hủy đăng ký / #{shortId}',
    en: 'Unsubscribe / #{shortId}',
  },
  'cleanup.unsubscribe.status.queued': {
    vi: 'Đang chờ tiến trình nền nhận việc',
    en: 'Waiting for worker',
  },
  'cleanup.unsubscribe.status.running': {
    vi: 'Đang chạy',
    en: 'Running',
  },
  'cleanup.unsubscribe.status.completed': {
    vi: 'Hoàn tất',
    en: 'Completed',
  },
  'cleanup.unsubscribe.status.failed': {
    vi: 'Lỗi - chưa hoàn tất',
    en: 'Error — did not complete',
  },
  'cleanup.unsubscribe.status.progress': {
    vi: '{percent}% ({okCount} thành công / {failedCount} lỗi / {totalCount} người gửi)',
    en: '{percent}% ({okCount} OK / {failedCount} failed / {totalCount} senders)',
  },
  'cleanup.unsubscribe.status.col.sender': {
    vi: 'Người gửi',
    en: 'Sender',
  },
  'cleanup.unsubscribe.status.col.state': {
    vi: 'Trạng thái',
    en: 'State',
  },
  'cleanup.unsubscribe.status.col.archived': {
    vi: 'Email đã lưu trữ',
    en: 'Mail archived',
  },
  'cleanup.unsubscribe.status.col.action': {
    vi: 'Hành động',
    en: 'Action',
  },
  'cleanup.unsubscribe.status.state.pending': {
    vi: 'Chờ',
    en: 'Pending',
  },
  'cleanup.unsubscribe.status.state.running': {
    vi: 'Đang chạy',
    en: 'Running',
  },
  'cleanup.unsubscribe.status.state.ok': {
    vi: 'Thành công',
    en: 'OK',
  },
  'cleanup.unsubscribe.status.state.failed': {
    vi: 'Thất bại',
    en: 'Failed',
  },
  'cleanup.unsubscribe.status.retry': {
    vi: 'Thử lại',
    en: 'Retry',
  },
  'cleanup.unsubscribe.status.retryOk': {
    vi: 'Đã đưa {sender} vào hàng đợi thử lại.',
    en: 'Retry enqueued for {sender}.',
  },
  'cleanup.unsubscribe.status.errorBanner': {
    vi: 'Lỗi hệ thống - chưa hoàn tất. Liên hệ bộ phận hỗ trợ nếu cần.',
    en: 'System error — did not complete. Contact support if needed.',
  },
  'cleanup.unsubscribe.status.error': {
    vi: 'Không tải được trạng thái hủy đăng ký. Hãy thử lại sau một chút.',
    en: 'Could not load unsubscribe status. Please try again shortly.',
  },
  'cleanup.unsubscribe.status.undo.title': {
    vi: 'Tác vụ đã hoàn tất',
    en: 'Cleanup completed',
  },
  'cleanup.unsubscribe.status.undo.body': {
    vi: 'Bạn có thể hoàn tác trong {daysLeft} ngày tới: khôi phục email về Hộp thư đến và gỡ nhãn `Zero Mail/Unsubscribed`.',
    en: 'You can undo within the next {daysLeft} days: restore mail to INBOX and remove the `Zero Mail/Unsubscribed` label.',
  },
  'cleanup.unsubscribe.status.undo.button': {
    vi: 'Hoàn tác lưu trữ',
    en: 'Undo archive',
  },
  'cleanup.unsubscribe.retry.alreadyOk': {
    vi: 'Người gửi này đã hủy đăng ký thành công, không cần thử lại.',
    en: 'This sender has already unsubscribed — no retry needed.',
  },
  'cleanup.unsubscribe.retry.generic': {
    vi: 'Không thử lại được. Hãy thử lại sau một chút.',
    en: 'Retry failed. Please try again shortly.',
  },
  'cleanup.unsubscribe.undo.confirmTitle': {
    vi: 'Hoàn tác lưu trữ?',
    en: 'Undo archive?',
  },
  'cleanup.unsubscribe.undo.confirmBody': {
    vi: 'Sẽ khôi phục {count} email về Hộp thư đến và gỡ nhãn `Zero Mail/Unsubscribed`. Hành động này không thể đảo ngược thêm lần nữa.',
    en: 'This will restore {count} mail to INBOX and remove the `Zero Mail/Unsubscribed` label. This action cannot be reversed again.',
  },
  'cleanup.unsubscribe.undo.confirmCta': {
    vi: 'Đồng ý hoàn tác',
    en: 'Confirm undo',
  },
  'cleanup.unsubscribe.undo.cancel': {
    vi: 'Quay lại',
    en: 'Cancel',
  },
  'cleanup.unsubscribe.undo.windowExpired': {
    vi: 'Đã quá 30 ngày kể từ khi tác vụ chạy nên không thể hoàn tác nữa.',
    en: 'More than 30 days have passed since the cleanup ran — undo is no longer available.',
  },
  'cleanup.unsubscribe.undo.windowExpiredToast': {
    vi: 'Quá thời hạn 30 ngày nên không thể hoàn tác nữa.',
    en: 'Past the 30-day window — undo is no longer available.',
  },
  'cleanup.unsubscribe.undo.ok': {
    vi: 'Đã khôi phục {count} email về Hộp thư đến.',
    en: 'Restored {count} mail to INBOX.',
  },
  'cleanup.unsubscribe.undo.generic': {
    vi: 'Không hoàn tác được. Hãy thử lại sau một chút.',
    en: 'Undo failed. Please try again shortly.',
  },

  // Backend error-code translations (CONVENTIONS i18n parity gate - every
  // ErrorCodes.java cleanup constant must have a matching `errors.cleanup.*` leaf).
  'errors.cleanup.campaign.too_many_senders': {
    vi: 'Vượt giới hạn 25 người gửi. Hãy bỏ chọn bớt.',
    en: 'Sender limit of 25 exceeded. Please deselect some.',
  },
  'errors.cleanup.campaign.too_many_messages': {
    vi: 'Vượt giới hạn 2.000 email lịch sử. Hãy bỏ chọn bớt.',
    en: 'History mail limit of 2,000 exceeded. Please deselect some.',
  },
  'errors.cleanup.campaign.not_found': {
    vi: 'Không tìm thấy tác vụ hủy đăng ký. Hãy quay lại danh sách.',
    en: 'Unsubscribe task not found. Please return to the list.',
  },
  'errors.cleanup.campaign.undo_window_expired': {
    vi: 'Quá thời hạn 30 ngày nên không thể hoàn tác nữa.',
    en: 'Past the 30-day window — undo is no longer available.',
  },
  'errors.cleanup.campaign.retry_conflict': {
    vi: 'Người gửi này đã hủy đăng ký thành công, không cần thử lại.',
    en: 'This sender has already unsubscribed — no retry needed.',
  },
  'errors.cleanup.sender_suppressed': {
    vi: 'Người gửi này đang nằm trong danh sách an toàn. Hãy gỡ khỏi danh sách trước khi xử lý.',
    en: 'This sender is in the safe list. Remove it before processing.',
  },
} as const;
