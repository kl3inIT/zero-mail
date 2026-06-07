export const unsubscribeCampaignMessages = {
  // Page header (matches the title/description block other app pages render)
  'cleanup.unsubscribe.page.title': {
    vi: 'Hủy đăng ký',
    en: 'Unsubscribe',
  },
  'cleanup.unsubscribe.page.description': {
    vi: 'Hủy đăng ký hoặc chặn hàng loạt người gửi mà bạn không bao giờ đọc.',
    en: 'Bulk-unsubscribe or block senders whose email you never read.',
  },

  // Filter menu (Inbox Zero-style table)
  'cleanup.unsubscribe.filter.unhandled': {
    vi: 'Chưa xử lý',
    en: 'Unhandled',
  },
  'cleanup.unsubscribe.filter.all': {
    vi: 'Tất cả người gửi',
    en: 'All senders',
  },
  'cleanup.unsubscribe.filter.autoArchived': {
    vi: 'Tự động lưu trữ',
    en: 'Auto archived',
  },
  'cleanup.unsubscribe.filter.approved': {
    vi: 'Đã phê duyệt',
    en: 'Approved',
  },

  // Time-window menu
  'cleanup.unsubscribe.window.7d': {
    vi: '7 ngày qua',
    en: 'Last 7 days',
  },
  'cleanup.unsubscribe.window.30d': {
    vi: '30 ngày qua',
    en: 'Last 30 days',
  },
  'cleanup.unsubscribe.window.90d': {
    vi: '3 tháng qua',
    en: 'Last 3 months',
  },

  // Status pill + stats dialog
  'cleanup.unsubscribe.statusLabel.unhandled': {
    vi: 'Chưa xử lý',
    en: 'Unhandled',
  },
  'cleanup.unsubscribe.statusLabel.approved': {
    vi: 'Đã phê duyệt',
    en: 'Approved',
  },
  'cleanup.unsubscribe.statusLabel.unsubscribed': {
    vi: 'Đã hủy đăng ký',
    en: 'Unsubscribed',
  },
  'cleanup.unsubscribe.statusLabel.autoArchived': {
    vi: 'Tự động lưu trữ',
    en: 'Auto archived',
  },

  // List header + toolbar
  'cleanup.unsubscribe.list.searchPlaceholder': {
    vi: 'Tìm người gửi hoặc tên miền',
    en: 'Search sender or domain',
  },
  'cleanup.unsubscribe.list.loadMore': {
    vi: 'Tải thêm',
    en: 'Load more',
  },
  'cleanup.unsubscribe.list.loadingMore': {
    vi: 'Đang tải thêm người gửi…',
    en: 'Loading more senders…',
  },
  'cleanup.unsubscribe.list.refreshing': {
    vi: 'Đang cập nhật danh sách người gửi…',
    en: 'Refreshing sender list…',
  },
  'cleanup.unsubscribe.list.error': {
    vi: 'Không tải được danh sách người gửi. Hãy thử lại sau một chút.',
    en: 'Could not load the sender list. Please try again shortly.',
  },
  'cleanup.unsubscribe.list.retry': {
    vi: 'Thử lại',
    en: 'Retry',
  },
  'cleanup.unsubscribe.list.empty.title': {
    vi: 'Chưa tìm thấy người gửi nào',
    en: 'No senders yet',
  },
  'cleanup.unsubscribe.list.empty.body': {
    vi: 'Zero Mail sẽ hiển thị các người gửi đã quan sát được trong khoảng thời gian đã chọn.',
    en: 'Zero Mail will show senders observed within the selected time window.',
  },
  'cleanup.unsubscribe.list.selectAll': {
    vi: 'Chọn tất cả người gửi đang hiển thị',
    en: 'Select all visible senders',
  },
  'cleanup.unsubscribe.list.clear': {
    vi: 'Bỏ chọn',
    en: 'Clear selection',
  },
  'cleanup.unsubscribe.list.counter': {
    vi: '{count} trong {total} người gửi đã chọn',
    en: '{count} of {total} senders selected',
  },
  'cleanup.unsubscribe.list.counterOver': {
    vi: '{count} / 25 người gửi - vượt giới hạn',
    en: '{count} / 25 senders — over the limit',
  },
  'cleanup.unsubscribe.list.selectedMail': {
    vi: '{count} email lịch sử trong lựa chọn',
    en: '{count} history mail in selection',
  },

  // Columns
  'cleanup.unsubscribe.list.col.from': {
    vi: 'Người gửi',
    en: 'From',
  },
  'cleanup.unsubscribe.list.col.emails': {
    vi: 'Số email',
    en: 'Emails',
  },
  'cleanup.unsubscribe.list.col.read': {
    vi: 'Tỷ lệ đọc',
    en: 'Read',
  },

  // Row + bulk actions
  'cleanup.unsubscribe.list.action.unsubscribe': {
    vi: 'Hủy đăng ký',
    en: 'Unsubscribe',
  },
  'cleanup.unsubscribe.list.action.unsubscribeBlock': {
    vi: 'Hủy đăng ký / Chặn',
    en: 'Unsubscribe / Block',
  },
  'cleanup.unsubscribe.list.action.block': {
    vi: 'Chặn',
    en: 'Block',
  },
  'cleanup.unsubscribe.list.action.approve': {
    vi: 'Phê duyệt',
    en: 'Approve',
  },
  'cleanup.unsubscribe.list.action.unapprove': {
    vi: 'Bỏ phê duyệt',
    en: 'Unapprove',
  },
  'cleanup.unsubscribe.list.action.autoArchive': {
    vi: 'Tự động lưu trữ',
    en: 'Auto-archive',
  },
  'cleanup.unsubscribe.list.action.archive': {
    vi: 'Lưu trữ',
    en: 'Archive',
  },
  'cleanup.unsubscribe.list.action.delete': {
    vi: 'Xóa',
    en: 'Delete',
  },
  'cleanup.unsubscribe.list.action.archiveAll': {
    vi: 'Lưu trữ toàn bộ email từ người này',
    en: 'Archive all mail from this sender',
  },
  'cleanup.unsubscribe.list.action.deleteAll': {
    vi: 'Xóa toàn bộ email từ người này',
    en: 'Delete all mail from this sender',
  },
  'cleanup.unsubscribe.list.action.labelFuture': {
    vi: 'Gắn nhãn cho email tương lai',
    en: 'Label future mail',
  },
  'cleanup.unsubscribe.list.action.viewStats': {
    vi: 'Xem thống kê',
    en: 'View stats',
  },
  'cleanup.unsubscribe.list.action.viewGmail': {
    vi: 'Mở trong Gmail',
    en: 'Open in Gmail',
  },
  'cleanup.unsubscribe.list.action.menu': {
    vi: 'Tùy chọn thêm',
    en: 'More options',
  },

  // Method labels (still used by stats dialog)
  'cleanup.unsubscribe.method.oneClick': {
    vi: 'Hủy nhận an toàn',
    en: 'Safe unsubscribe',
  },
  'cleanup.unsubscribe.method.mailto': {
    vi: 'Gửi email hủy nhận',
    en: 'Unsubscribe by email',
  },
  'cleanup.unsubscribe.method.none': {
    vi: 'Không hỗ trợ',
    en: 'Unsupported',
  },

  // Sender stats dialog (Inbox Zero-style modal: chart + email list + body preview)
  'cleanup.unsubscribe.stats.titleWith': {
    vi: 'Thống kê cho {sender}',
    en: 'Stats for {sender}',
  },
  'cleanup.unsubscribe.stats.chartLoading': {
    vi: 'Đang tải biểu đồ…',
    en: 'Loading chart…',
  },
  'cleanup.unsubscribe.stats.chartError': {
    vi: 'Không tải được biểu đồ. Hãy thử lại sau một chút.',
    en: 'Could not load the chart. Please try again shortly.',
  },
  'cleanup.unsubscribe.stats.chartEmpty': {
    vi: 'Chưa có dữ liệu trong 30 ngày qua.',
    en: 'No data in the last 30 days.',
  },
  'cleanup.unsubscribe.stats.tabs.unarchived': {
    vi: 'Chưa lưu trữ',
    en: 'Unarchived',
  },
  'cleanup.unsubscribe.stats.tabs.all': {
    vi: 'Tất cả',
    en: 'All',
  },
  'cleanup.unsubscribe.stats.messagesLoading': {
    vi: 'Đang tải email từ Gmail…',
    en: 'Loading mail from Gmail…',
  },
  'cleanup.unsubscribe.stats.messagesError': {
    vi: 'Không tải được danh sách email. Hãy thử lại sau một chút.',
    en: 'Could not load the mail list. Please try again shortly.',
  },
  'cleanup.unsubscribe.stats.messagesEmpty': {
    vi: 'Không có email nào trong tab này.',
    en: 'No mail in this tab.',
  },
  'cleanup.unsubscribe.stats.previewHint': {
    vi: 'Chọn một email ở bên trái để xem nội dung.',
    en: 'Pick an email on the left to read it.',
  },
  'cleanup.unsubscribe.stats.previewLoading': {
    vi: 'Đang tải nội dung email…',
    en: 'Loading mail body…',
  },
  'cleanup.unsubscribe.stats.previewError': {
    vi: 'Không tải được nội dung email. Hãy thử lại.',
    en: 'Could not load the mail body. Please try again.',
  },
  'cleanup.unsubscribe.stats.previewEmpty': {
    vi: 'Email này không có nội dung hiển thị được.',
    en: 'This mail has no displayable body.',
  },
  'cleanup.unsubscribe.stats.closePreview': {
    vi: 'Đóng phần xem trước',
    en: 'Close preview',
  },

  // Confirm dialogs (archive + delete)
  'cleanup.unsubscribe.confirm.cancel': {
    vi: 'Hủy',
    en: 'Cancel',
  },
  'cleanup.unsubscribe.confirm.archiveTitle': {
    vi: 'Lưu trữ toàn bộ email từ {count} người gửi?',
    en: 'Archive all mail from {count} senders?',
  },
  'cleanup.unsubscribe.confirm.archiveBody': {
    vi: 'Zero Mail sẽ chuyển toàn bộ email lịch sử của {count} người gửi này ra khỏi Hộp thư đến. Email không bị xóa và có thể tự kéo lại từ Gmail.',
    en: 'Zero Mail will move every history mail from these {count} senders out of the Inbox. No mail is deleted and you can move it back from Gmail.',
  },
  'cleanup.unsubscribe.confirm.deleteTitle': {
    vi: 'Xóa toàn bộ email từ {count} người gửi?',
    en: 'Delete all mail from {count} senders?',
  },
  'cleanup.unsubscribe.confirm.deleteBody': {
    vi: 'Zero Mail sẽ chuyển toàn bộ email lịch sử của {count} người gửi này vào Thùng rác Gmail. Bạn có 30 ngày để khôi phục từ Gmail trước khi Gmail xóa vĩnh viễn.',
    en: 'Zero Mail will move every history mail from these {count} senders to Gmail Trash. You have 30 days to restore them from Gmail before they are permanently deleted.',
  },
  'cleanup.unsubscribe.confirm.deleteOne': {
    vi: 'Xóa toàn bộ email từ {sender}? Email sẽ chuyển vào Thùng rác Gmail.',
    en: 'Delete all mail from {sender}? Mail will be moved to Gmail Trash.',
  },

  // Label-future dialog
  'cleanup.unsubscribe.labelFuture.title': {
    vi: 'Gắn nhãn cho email tương lai từ người gửi này',
    en: 'Label future mail from this sender',
  },
  'cleanup.unsubscribe.labelFuture.body': {
    vi: 'Zero Mail sẽ tạo quy tắc tự động gắn nhãn cho email mới từ {sender}. Email lịch sử không bị thay đổi.',
    en: 'Zero Mail will create a rule that auto-labels new mail from {sender}. History mail is left alone.',
  },
  'cleanup.unsubscribe.labelFuture.placeholder': {
    vi: 'Tên nhãn (ví dụ: Newsletters)',
    en: 'Label name (e.g. Newsletters)',
  },

  // Toasts from useSenderAction + useExecuteCampaign
  'cleanup.unsubscribe.action.mailAffected': {
    vi: '{count} email đã được xử lý.',
    en: '{count} mail processed.',
  },
  'cleanup.unsubscribe.action.genericError': {
    vi: 'Không thực hiện được. Hãy thử lại sau một chút.',
    en: 'Action failed. Please try again shortly.',
  },
  'cleanup.unsubscribe.action.approveOk': {
    vi: 'Đã phê duyệt người gửi.',
    en: 'Sender approved.',
  },
  'cleanup.unsubscribe.action.unapproveOk': {
    vi: 'Đã bỏ phê duyệt.',
    en: 'Approval removed.',
  },
  'cleanup.unsubscribe.action.markUnsubscribedOk': {
    vi: 'Đã đánh dấu đã hủy đăng ký.',
    en: 'Marked as unsubscribed.',
  },
  'cleanup.unsubscribe.action.autoArchiveOk': {
    vi: 'Đã tạo quy tắc tự động lưu trữ cho người gửi này. Email cũ đã chuyển khỏi Hộp thư đến; email mới sẽ tự động bị lưu trữ.',
    en: 'Created an auto-archive rule for this sender. Old mail moved out of the Inbox; new mail will be archived automatically.',
  },
  'cleanup.unsubscribe.action.blockOk': {
    vi: 'Đã chặn người gửi: Zero Mail tạo quy tắc tự động lưu trữ. Email cũ đã chuyển khỏi Hộp thư đến; email mới sẽ tự động bị lưu trữ.',
    en: 'Sender blocked: Zero Mail created an auto-archive rule. Old mail moved out of the Inbox; new mail will be archived automatically.',
  },
  'cleanup.unsubscribe.action.archiveOk': {
    vi: 'Đã lưu trữ email cũ của người gửi này. Người gửi vẫn ở trong danh sách để bạn quyết định hủy đăng ký hoặc chặn.',
    en: 'Old mail from this sender archived. The sender stays in the list so you can still unsubscribe or block.',
  },
  'cleanup.unsubscribe.action.deleteOk': {
    vi: 'Đã chuyển email lịch sử vào Thùng rác.',
    en: 'History mail moved to Trash.',
  },
  'cleanup.unsubscribe.action.labelFutureOk': {
    vi: 'Đã tạo quy tắc gắn nhãn cho email tương lai.',
    en: 'Future-mail label rule created.',
  },
  'cleanup.unsubscribe.action.submitOk': {
    vi: 'Đã tạo tác vụ hủy đăng ký',
    en: 'Unsubscribe task created',
  },
  'cleanup.unsubscribe.action.submitDescription': {
    vi: 'Zero Mail sẽ xử lý {count} người gửi. Bạn có thể tiếp tục dùng ứng dụng.',
    en: 'Zero Mail will process {count} senders. You can keep using the app.',
  },
  'cleanup.unsubscribe.action.errCapSender': {
    vi: 'Vượt giới hạn 25 người gửi. Hãy bỏ chọn bớt.',
    en: 'Sender limit of 25 exceeded. Please deselect some.',
  },
  'cleanup.unsubscribe.action.errCapMessage': {
    vi: 'Vượt giới hạn 2.000 email lịch sử. Hãy bỏ chọn bớt.',
    en: 'History mail limit of 2,000 exceeded. Please deselect some.',
  },
  'cleanup.unsubscribe.action.errGeneric': {
    vi: 'Không tạo được tác vụ hủy đăng ký. Hãy thử lại sau một chút.',
    en: 'Could not create the unsubscribe task. Please try again shortly.',
  },

  // Backend error-code translations (CONVENTIONS i18n parity gate).
  'errors.cleanup.campaign.too_many_senders': {
    vi: 'Vượt giới hạn 25 người gửi. Hãy bỏ chọn bớt.',
    en: 'Sender limit of 25 exceeded. Please deselect some.',
  },
  'errors.cleanup.campaign.too_many_messages': {
    vi: 'Vượt giới hạn 2.000 email lịch sử. Hãy bỏ chọn bớt.',
    en: 'History mail limit of 2,000 exceeded. Please deselect some.',
  },
  'errors.cleanup.sender_suppressed': {
    vi: 'Người gửi này đang nằm trong danh sách an toàn. Hãy gỡ khỏi danh sách trước khi xử lý.',
    en: 'This sender is in the safe list. Remove it before processing.',
  },
} as const;
