export const billingMessages = {
  'billing.balance.label': {
    vi: 'Tín dụng hiện có',
    en: 'Available credits',
  },
  'billing.balance.held': {
    vi: 'Đang giữ',
    en: 'Held credits',
  },
  'billing.balance.topupCta': {
    vi: 'Nạp tín dụng',
    en: 'Top up credits',
  },
  'billing.ledger.title': {
    vi: 'Lịch sử giao dịch',
    en: 'Transaction history',
  },
  'billing.ledger.empty.heading': {
    vi: 'Chưa có giao dịch',
    en: 'No transactions yet',
  },
  'billing.ledger.empty.body': {
    vi: 'Các lượt nạp và sử dụng tín dụng sẽ xuất hiện ở đây. Hãy nạp tín dụng để bắt đầu.',
    en: 'Your top-ups and credit usage will show up here. Add credits to get started.',
  },
  'billing.ledger.unavailable.heading': {
    vi: 'Lịch sử giao dịch chưa khả dụng',
    en: "Transaction history isn't available yet",
  },
  'billing.ledger.unavailable.body': {
    vi: 'Zero Mail đã có số dư tín dụng, nhưng danh sách giao dịch chi tiết sẽ được bật trong một bản cập nhật sau.',
    en: 'Zero Mail can show your credit balance now. Detailed transaction history will arrive in a later update.',
  },
  'billing.topup.amount.label': {
    vi: 'Số tiền nạp',
    en: 'Top-up amount',
  },
  'billing.topup.amount.cta': {
    vi: 'Tạo mã chuyển khoản',
    en: 'Create transfer code',
  },
  'billing.topup.qr.heading': {
    vi: 'Quét mã QR bằng ứng dụng ngân hàng',
    en: 'Scan this QR with your banking app',
  },
  'billing.topup.qr.body': {
    vi: 'Mã VietQR đã bao gồm tài khoản nhận, số tiền và mã tham chiếu.',
    en: 'The VietQR payload already includes the destination account, amount, and reference.',
  },
  'billing.topup.reference.label': {
    vi: 'Mã tham chiếu',
    en: 'Transfer reference',
  },
  'billing.topup.amountVnd.label': {
    vi: 'Số tiền VND',
    en: 'Amount in VND',
  },
  'billing.topup.expiresAt.label': {
    vi: 'Hết hạn lúc',
    en: 'Expires at',
  },
  'billing.topup.waiting.heading': {
    vi: 'Đang chờ ghi nhận chuyển khoản',
    en: 'Waiting for your transfer',
  },
  'billing.topup.waiting.body': {
    vi: 'Zero Mail sẽ tự cập nhật số dư khi webhook ghi nhận giao dịch.',
    en: 'Zero Mail will update your balance when the webhook credits the transfer.',
  },
  'billing.topup.success.heading': {
    vi: 'Đã cộng tín dụng',
    en: 'Credits added',
  },
  'billing.topup.success.body': {
    vi: 'Số dư tín dụng đã tăng. Bạn có thể tiếp tục dùng Zero Mail.',
    en: 'Your credit balance increased. You can keep using Zero Mail.',
  },
  'billing.topup.expired.heading': {
    vi: 'Mã nạp này đã hết hạn',
    en: 'This top-up expired',
  },
  'billing.topup.expired.body': {
    vi: 'Zero Mail không thấy chuyển khoản đúng hạn, nên mã này không còn được theo dõi. Hãy tạo mã mới.',
    en: "We didn't see the transfer in time, so this code is no longer being watched. Create a new one.",
  },
  'billing.copy.cta': {
    vi: 'Sao chép',
    en: 'Copy',
  },
  'billing.copy.done': {
    vi: 'Đã sao chép',
    en: 'Copied',
  },
} as const;
