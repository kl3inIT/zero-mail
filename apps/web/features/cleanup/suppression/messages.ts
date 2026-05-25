export const suppressionMessages = {
  'cleanup.suppression.title': {
    vi: 'Danh sách an toàn',
    en: 'Safe list',
  },
  'cleanup.suppression.lead': {
    vi: 'Người gửi hoặc tên miền trong danh sách này sẽ không xuất hiện trong gợi ý hủy đăng ký.',
    en: 'Senders or domains on this list will not appear in unsubscribe suggestions.',
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
    vi: 'Thêm vào danh sách an toàn',
    en: 'Add to safe list',
  },
  'cleanup.suppression.helper': {
    vi: 'Mỗi mục bỏ qua một người gửi hoặc cả một tên miền.',
    en: 'Each entry skips one sender or an entire domain.',
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
    vi: 'Gỡ khỏi danh sách an toàn',
    en: 'Remove from safe list',
  },
  'cleanup.suppression.remove.confirmTitle': {
    vi: 'Gỡ mục này?',
    en: 'Remove this entry?',
  },
  'cleanup.suppression.remove.confirmBody': {
    vi: 'Sau khi gỡ, người gửi hoặc tên miền này có thể xuất hiện lại trong gợi ý hủy đăng ký.',
    en: 'After removal, this sender or domain can reappear in unsubscribe suggestions.',
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
    vi: 'Mục này đã có trong danh sách an toàn.',
    en: 'This entry is already in the safe list.',
  },
  'cleanup.suppression.err.generic': {
    vi: 'Không thêm được. Hãy thử lại sau một chút.',
    en: 'Could not add. Please try again shortly.',
  },
  'cleanup.suppression.removeOk': {
    vi: 'Đã gỡ khỏi danh sách an toàn.',
    en: 'Removed from safe list.',
  },
  'cleanup.suppression.addOk': {
    vi: 'Đã thêm vào danh sách an toàn.',
    en: 'Added to safe list.',
  },
  'cleanup.suppression.empty.title': {
    vi: 'Chưa có người gửi nào trong danh sách an toàn',
    en: 'No senders in the safe list yet',
  },
  'cleanup.suppression.empty.body': {
    vi: 'Thêm người gửi hoặc tên miền bạn không muốn Zero Mail đề xuất hủy đăng ký, ví dụ: sếp, đồng nghiệp hoặc ngân hàng.',
    en: 'Add senders or domains you do not want Zero Mail to suggest unsubscribing from, such as your boss, colleagues, or bank.',
  },
  'cleanup.suppression.error': {
    vi: 'Không tải được danh sách. Hãy thử lại sau một chút.',
    en: 'Could not load the list. Please try again shortly.',
  },
} as const;
