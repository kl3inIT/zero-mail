export const suppressionMessages = {
  'cleanup.suppression.title': {
    vi: 'Danh sách bảo vệ',
    en: 'Suppression list',
  },
  'cleanup.suppression.lead': {
    vi: 'Người gửi hoặc tên miền trong danh sách này sẽ không bao giờ hiển thị trong chiến dịch hủy đăng ký.',
    en: 'Senders or domains on this list will never appear in an unsubscribe campaign.',
  },
  'cleanup.suppression.input.placeholder': {
    vi: 'Email hoặc tên miền (ví dụ: boss@example.com hoặc example.com)',
    en: 'Email or domain (e.g. boss@example.com or example.com)',
  },
  'cleanup.suppression.input.label': {
    vi: 'Email người gửi',
    en: 'Sender email',
  },
  'cleanup.suppression.add': {
    vi: 'Thêm vào danh sách bảo vệ',
    en: 'Add to suppression',
  },
  'cleanup.suppression.helper': {
    vi: 'Mỗi mục bảo vệ một người gửi hoặc cả một tên miền.',
    en: 'Each entry blocks a single sender or an entire domain.',
  },
  'cleanup.suppression.col.target': {
    vi: 'Người gửi / Tên miền',
    en: 'Sender / Domain',
  },
  'cleanup.suppression.col.source': {
    vi: 'Nguồn',
    en: 'Source',
  },
  'cleanup.suppression.col.added': {
    vi: 'Thêm lúc',
    en: 'Added',
  },
  'cleanup.suppression.source.manual': {
    vi: 'Thủ công',
    en: 'Manual',
  },
  'cleanup.suppression.source.replied': {
    vi: 'Đã trả lời',
    en: 'Replied',
  },
  'cleanup.suppression.source.auto': {
    vi: 'Tự động',
    en: 'Auto',
  },
  'cleanup.suppression.remove.aria': {
    vi: 'Gỡ khỏi danh sách bảo vệ',
    en: 'Remove from suppression',
  },
  'cleanup.suppression.remove.confirmTitle': {
    vi: 'Gỡ mục này?',
    en: 'Remove this entry?',
  },
  'cleanup.suppression.remove.confirmBody': {
    vi: 'Sau khi gỡ, người gửi hoặc tên miền này sẽ xuất hiện lại trong danh sách ứng viên.',
    en: 'After removal, this sender or domain will reappear as a campaign candidate.',
  },
  'cleanup.suppression.remove.confirmCta': {
    vi: 'Đồng ý gỡ',
    en: 'Confirm remove',
  },
  'cleanup.suppression.remove.cancel': {
    vi: 'Quay lại',
    en: 'Cancel',
  },
  'cleanup.suppression.err.invalid': {
    vi: 'Email hoặc tên miền không hợp lệ.',
    en: 'Invalid email or domain.',
  },
  'cleanup.suppression.err.duplicate': {
    vi: 'Mục này đã có trong danh sách bảo vệ.',
    en: 'This entry is already in the suppression list.',
  },
  'cleanup.suppression.err.generic': {
    vi: 'Không thêm được. Hãy thử lại sau một chút.',
    en: 'Could not add. Please try again shortly.',
  },
  'cleanup.suppression.removeOk': {
    vi: 'Đã gỡ khỏi danh sách bảo vệ.',
    en: 'Removed from suppression list.',
  },
  'cleanup.suppression.addOk': {
    vi: 'Đã thêm vào danh sách bảo vệ.',
    en: 'Added to suppression list.',
  },
  'cleanup.suppression.empty.title': {
    vi: 'Chưa có người gửi nào trong danh sách bảo vệ',
    en: 'No senders in the suppression list yet',
  },
  'cleanup.suppression.empty.body': {
    vi: 'Thêm người gửi hoặc tên miền bạn không bao giờ muốn hủy đăng ký, ví dụ: sếp, đồng nghiệp hoặc ngân hàng.',
    en: 'Add senders or domains you never want to unsubscribe (e.g. boss, colleagues, bank).',
  },
  'cleanup.suppression.error': {
    vi: 'Không tải được danh sách. Hãy thử lại sau một chút.',
    en: 'Could not load the list. Please try again shortly.',
  },
} as const;
