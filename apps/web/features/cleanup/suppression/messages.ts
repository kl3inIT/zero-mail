export const suppressionMessages = {
  'cleanup.suppression.title': {
    vi: 'Suppression list',
    en: 'Suppression list',
  },
  'cleanup.suppression.lead': {
    vi: 'Sender hoặc domain trong danh sách này sẽ không bao giờ hiển thị trong campaign unsubscribe.',
    en: 'Senders or domains on this list will never appear in an unsubscribe campaign.',
  },
  'cleanup.suppression.input.placeholder': {
    vi: 'Email hoặc domain (ví dụ: boss@example.com hoặc example.com)',
    en: 'Email or domain (e.g. boss@example.com or example.com)',
  },
  'cleanup.suppression.input.label': {
    vi: 'Email người gửi',
    en: 'Sender email',
  },
  'cleanup.suppression.add': {
    vi: 'Thêm vào suppression',
    en: 'Add to suppression',
  },
  'cleanup.suppression.helper': {
    vi: 'Mỗi entry chặn 1 sender hoặc cả 1 domain.',
    en: 'Each entry blocks a single sender or an entire domain.',
  },
  'cleanup.suppression.col.target': {
    vi: 'Sender / Domain',
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
    vi: 'Đã reply',
    en: 'Replied',
  },
  'cleanup.suppression.source.auto': {
    vi: 'Tự động',
    en: 'Auto',
  },
  'cleanup.suppression.remove.aria': {
    vi: 'Xóa khỏi suppression',
    en: 'Remove from suppression',
  },
  'cleanup.suppression.remove.confirmTitle': {
    vi: 'Xóa entry này?',
    en: 'Remove this entry?',
  },
  'cleanup.suppression.remove.confirmBody': {
    vi: 'Sau khi xóa, sender / domain này sẽ lại xuất hiện trong campaign ứng viên.',
    en: 'After removal, this sender or domain will reappear as a campaign candidate.',
  },
  'cleanup.suppression.remove.confirmCta': {
    vi: 'Đồng ý xóa',
    en: 'Confirm remove',
  },
  'cleanup.suppression.remove.cancel': {
    vi: 'Quay lại',
    en: 'Cancel',
  },
  'cleanup.suppression.err.invalid': {
    vi: 'Email hoặc domain không hợp lệ.',
    en: 'Invalid email or domain.',
  },
  'cleanup.suppression.err.duplicate': {
    vi: 'Entry này đã có trong suppression list.',
    en: 'This entry is already in the suppression list.',
  },
  'cleanup.suppression.err.generic': {
    vi: 'Không thêm được. Hãy thử lại sau một chút.',
    en: 'Could not add. Please try again shortly.',
  },
  'cleanup.suppression.removeOk': {
    vi: 'Đã xóa khỏi suppression list.',
    en: 'Removed from suppression list.',
  },
  'cleanup.suppression.addOk': {
    vi: 'Đã thêm vào suppression list.',
    en: 'Added to suppression list.',
  },
  'cleanup.suppression.empty.title': {
    vi: 'Chưa có sender nào trong suppression list',
    en: 'No senders in the suppression list yet',
  },
  'cleanup.suppression.empty.body': {
    vi: 'Thêm sender hoặc domain bạn không bao giờ muốn unsubscribe (ví dụ: sếp, đồng nghiệp, ngân hàng).',
    en: 'Add senders or domains you never want to unsubscribe (e.g. boss, colleagues, bank).',
  },
  'cleanup.suppression.error': {
    vi: 'Không tải được danh sách. Hãy thử lại sau một chút.',
    en: 'Could not load the list. Please try again shortly.',
  },
} as const;
