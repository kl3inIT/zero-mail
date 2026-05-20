export const unsubscribeCampaignMessages = {
  // List page
  'cleanup.unsubscribe.list.title': {
    vi: 'Bulk unsubscribe',
    en: 'Bulk unsubscribe',
  },
  'cleanup.unsubscribe.list.lead': {
    vi: 'Chọn các newsletter bạn muốn dừng nhận. Zero Mail sẽ unsubscribe an toàn và archive lịch sử mail từ những sender đã chọn.',
    en: 'Pick the newsletters you want to stop receiving. Zero Mail unsubscribes safely and archives the history from the selected senders.',
  },
  'cleanup.unsubscribe.list.suppressionLink': {
    vi: 'Quản lý suppression list →',
    en: 'Manage suppression list →',
  },
  'cleanup.unsubscribe.list.counter': {
    vi: '{count} / 25 sender đã chọn',
    en: '{count} / 25 senders selected',
  },
  'cleanup.unsubscribe.list.counterOver': {
    vi: '{count} / 25 sender — vượt giới hạn',
    en: '{count} / 25 senders — over the limit',
  },
  'cleanup.unsubscribe.list.preview': {
    vi: 'Xem trước campaign',
    en: 'Preview campaign',
  },
  'cleanup.unsubscribe.list.clear': {
    vi: 'Bỏ chọn',
    en: 'Clear selection',
  },
  'cleanup.unsubscribe.list.col.sender': {
    vi: 'Sender',
    en: 'Sender',
  },
  'cleanup.unsubscribe.list.col.domain': {
    vi: 'Tên miền',
    en: 'Domain',
  },
  'cleanup.unsubscribe.list.col.count': {
    vi: 'Mail 30 ngày',
    en: 'Mail (30 days)',
  },
  'cleanup.unsubscribe.list.col.method': {
    vi: 'Phương thức',
    en: 'Method',
  },
  'cleanup.unsubscribe.list.col.risk': {
    vi: 'Trạng thái',
    en: 'Status',
  },
  'cleanup.unsubscribe.method.oneClick': {
    vi: 'One-click (RFC 8058)',
    en: 'One-click (RFC 8058)',
  },
  'cleanup.unsubscribe.method.mailto': {
    vi: 'Mailto',
    en: 'Mailto',
  },
  'cleanup.unsubscribe.method.none': {
    vi: 'Không hỗ trợ',
    en: 'Unsupported',
  },
  'cleanup.unsubscribe.risk.safe': {
    vi: 'Sẵn sàng',
    en: 'Ready',
  },
  'cleanup.unsubscribe.risk.noHeader': {
    vi: 'Chưa hỗ trợ',
    en: 'Not supported',
  },
  'cleanup.unsubscribe.risk.noHeaderTooltip': {
    vi: 'Sender này không có header `List-Unsubscribe` — chưa thể unsubscribe tự động.',
    en: 'This sender has no `List-Unsubscribe` header — automatic unsubscribe is unavailable.',
  },
  'cleanup.unsubscribe.risk.suppressed': {
    vi: 'Đã chặn',
    en: 'Blocked',
  },
  'cleanup.unsubscribe.list.empty.title': {
    vi: 'Chưa có newsletter nào trong 30 ngày qua',
    en: 'No newsletters in the last 30 days',
  },
  'cleanup.unsubscribe.list.empty.body': {
    vi: 'Zero Mail phát hiện newsletter qua header `List-Unsubscribe` từ ingest gần đây. Khi có dữ liệu mới, sender ứng viên sẽ xuất hiện ở đây.',
    en: 'Zero Mail detects newsletters via the `List-Unsubscribe` header from recent ingest. New candidate senders will appear here as data arrives.',
  },
  'cleanup.unsubscribe.list.empty.link': {
    vi: 'Tìm hiểu Zero Mail phát hiện newsletter thế nào →',
    en: 'Learn how Zero Mail detects newsletters →',
  },
  'cleanup.unsubscribe.list.error': {
    vi: 'Không tải được danh sách sender. Hãy thử lại sau một chút.',
    en: 'Could not load the sender list. Please try again shortly.',
  },
  'cleanup.unsubscribe.list.retry': {
    vi: 'Thử lại',
    en: 'Retry',
  },

  // Preview dialog
  'cleanup.unsubscribe.preview.title': {
    vi: 'Xem trước campaign',
    en: 'Preview campaign',
  },
  'cleanup.unsubscribe.preview.description': {
    vi: 'Kiểm tra lại danh sách trước khi execute. Campaign sẽ chạy nền và có thể undo trong 30 ngày.',
    en: 'Review the list before executing. The campaign runs in the background and is undoable for 30 days.',
  },
  'cleanup.unsubscribe.preview.totalSender': {
    vi: 'Tổng sender: {count}',
    en: 'Total senders: {count}',
  },
  'cleanup.unsubscribe.preview.totalMail': {
    vi: 'Tổng mail sẽ archive: {count}',
    en: 'Total mail to archive: {count}',
  },
  'cleanup.unsubscribe.preview.capSender': {
    vi: 'Đã vượt giới hạn 25 sender. Quay lại và bỏ chọn bớt.',
    en: 'Sender limit of 25 exceeded. Go back and deselect some.',
  },
  'cleanup.unsubscribe.preview.capMessage': {
    vi: 'Đã vượt giới hạn 2.000 mail lịch sử ({count}). Quay lại và bỏ chọn bớt.',
    en: 'History mail limit of 2,000 exceeded ({count}). Go back and deselect some.',
  },
  'cleanup.unsubscribe.preview.cancel': {
    vi: 'Quay lại',
    en: 'Back',
  },
  'cleanup.unsubscribe.preview.confirm': {
    vi: 'Execute campaign',
    en: 'Execute campaign',
  },
  'cleanup.unsubscribe.preview.submitting': {
    vi: 'Đang tạo…',
    en: 'Creating…',
  },
  'cleanup.unsubscribe.preview.willArchive': {
    vi: '{count} mail sẽ archive',
    en: '{count} mail will be archived',
  },
  'cleanup.unsubscribe.preview.willNotArchive': {
    vi: 'Không archive (thiếu header)',
    en: 'No archive (missing header)',
  },
  'cleanup.unsubscribe.preview.submitOk': {
    vi: 'Campaign đã được tạo. Đang theo dõi tiến độ…',
    en: 'Campaign created. Tracking progress…',
  },
  'cleanup.unsubscribe.preview.errCapSender': {
    vi: 'Vượt giới hạn 25 sender. Hãy bỏ chọn bớt.',
    en: 'Sender limit of 25 exceeded. Please deselect some.',
  },
  'cleanup.unsubscribe.preview.errCapMessage': {
    vi: 'Vượt giới hạn 2.000 mail lịch sử. Hãy bỏ chọn bớt.',
    en: 'History mail limit of 2,000 exceeded. Please deselect some.',
  },
  'cleanup.unsubscribe.preview.errGeneric': {
    vi: 'Không tạo được campaign. Hãy thử lại sau một chút.',
    en: 'Could not create the campaign. Please try again shortly.',
  },

  // Status page
  'cleanup.unsubscribe.status.title': {
    vi: 'Campaign #{shortId}',
    en: 'Campaign #{shortId}',
  },
  'cleanup.unsubscribe.status.breadcrumb': {
    vi: 'Cleanup / Unsubscribe / #{shortId}',
    en: 'Cleanup / Unsubscribe / #{shortId}',
  },
  'cleanup.unsubscribe.status.queued': {
    vi: 'Đang chờ worker pick',
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
    vi: 'Lỗi — không hoàn tất',
    en: 'Error — did not complete',
  },
  'cleanup.unsubscribe.status.progress': {
    vi: '{percent}% ({okCount} OK / {failedCount} lỗi / {totalCount} sender)',
    en: '{percent}% ({okCount} OK / {failedCount} failed / {totalCount} senders)',
  },
  'cleanup.unsubscribe.status.col.sender': {
    vi: 'Sender',
    en: 'Sender',
  },
  'cleanup.unsubscribe.status.col.state': {
    vi: 'Trạng thái',
    en: 'State',
  },
  'cleanup.unsubscribe.status.col.archived': {
    vi: 'Mail đã archive',
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
    vi: 'Đã enqueue thử lại cho {sender}.',
    en: 'Retry enqueued for {sender}.',
  },
  'cleanup.unsubscribe.status.errorBanner': {
    vi: 'Lỗi hệ thống — không hoàn tất. Liên hệ support nếu cần.',
    en: 'System error — did not complete. Contact support if needed.',
  },
  'cleanup.unsubscribe.status.error': {
    vi: 'Không tải được trạng thái campaign. Hãy thử lại sau một chút.',
    en: 'Could not load campaign status. Please try again shortly.',
  },
  'cleanup.unsubscribe.status.undo.title': {
    vi: 'Campaign đã hoàn tất',
    en: 'Campaign completed',
  },
  'cleanup.unsubscribe.status.undo.body': {
    vi: 'Bạn có thể undo trong {daysLeft} ngày tới: restore mail về INBOX và remove label `Zero Mail/Unsubscribed`.',
    en: 'You can undo within the next {daysLeft} days: restore mail to INBOX and remove the `Zero Mail/Unsubscribed` label.',
  },
  'cleanup.unsubscribe.status.undo.button': {
    vi: 'Undo campaign',
    en: 'Undo campaign',
  },
  'cleanup.unsubscribe.retry.alreadyOk': {
    vi: 'Sender này đã unsubscribe thành công, không cần thử lại.',
    en: 'This sender has already unsubscribed — no retry needed.',
  },
  'cleanup.unsubscribe.retry.generic': {
    vi: 'Không thử lại được. Hãy thử lại sau một chút.',
    en: 'Retry failed. Please try again shortly.',
  },
  'cleanup.unsubscribe.undo.confirmTitle': {
    vi: 'Undo campaign?',
    en: 'Undo campaign?',
  },
  'cleanup.unsubscribe.undo.confirmBody': {
    vi: 'Sẽ restore {count} mail về INBOX và remove label `Zero Mail/Unsubscribed`. Hành động này không thể đảo ngược lần nữa.',
    en: 'This will restore {count} mail to INBOX and remove the `Zero Mail/Unsubscribed` label. This action cannot be reversed again.',
  },
  'cleanup.unsubscribe.undo.confirmCta': {
    vi: 'Đồng ý undo',
    en: 'Confirm undo',
  },
  'cleanup.unsubscribe.undo.cancel': {
    vi: 'Quay lại',
    en: 'Cancel',
  },
  'cleanup.unsubscribe.undo.windowExpired': {
    vi: 'Đã quá 30 ngày kể từ khi campaign chạy — không undo được nữa.',
    en: 'More than 30 days have passed since the campaign — undo is no longer available.',
  },
  'cleanup.unsubscribe.undo.windowExpiredToast': {
    vi: 'Quá window 30 ngày — không undo được nữa.',
    en: 'Past the 30-day window — undo is no longer available.',
  },
  'cleanup.unsubscribe.undo.ok': {
    vi: 'Đã restore {count} mail về INBOX.',
    en: 'Restored {count} mail to INBOX.',
  },
  'cleanup.unsubscribe.undo.generic': {
    vi: 'Không undo được. Hãy thử lại sau một chút.',
    en: 'Undo failed. Please try again shortly.',
  },
} as const;
