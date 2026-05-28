export const billingMessages = {
  'billing.page.title': {
    vi: 'Thanh toán',
    en: 'Billing',
  },
  'billing.page.description': {
    vi: 'Theo dõi tín dụng beta miễn phí, tín dụng đã nạp và lịch sử sử dụng gần đây.',
    en: 'Track free beta credits, paid credits, and recent usage activity.',
  },
  'billing.balance.label': {
    vi: 'Tín dụng hiện có',
    en: 'Available credits',
  },
  'billing.balance.description': {
    vi: 'Số dư dùng cho các tác vụ AI của Zero Mail.',
    en: 'Balance available for Zero Mail AI actions.',
  },
  'billing.balance.unit': {
    vi: 'tín dụng',
    en: 'credits',
  },
  'billing.balance.held': {
    vi: 'Đang giữ',
    en: 'Held credits',
  },
  'billing.balance.heldDetail': {
    vi: 'Đã reserve cho tác vụ đang chạy',
    en: 'Reserved by in-flight actions',
  },
  'billing.balance.betaCredits': {
    vi: 'Tín dụng beta',
    en: 'Beta credits',
  },
  'billing.balance.paidCredits': {
    vi: 'Tín dụng trả phí',
    en: 'Paid credits',
  },
  'billing.balance.monthlyGrant': {
    vi: '{credits} tín dụng / tháng',
    en: '{credits} credits / month',
  },
  'billing.balance.noExpiry': {
    vi: 'Không reset theo kỳ beta',
    en: 'Does not reset with beta',
  },
  'billing.balance.resetsAt': {
    vi: 'Reset lúc',
    en: 'Resets at',
  },
  'billing.balance.resetUnknown': {
    vi: 'Chưa rõ',
    en: 'Unknown',
  },
  'billing.balance.betaNotice': {
    vi: 'Miễn phí trong giai đoạn beta. Zero Mail sẽ báo trước khi cần chuyển sang gói trả phí.',
    en: "Free during beta. We'll notify you before paid plans are required.",
  },
  'billing.balance.refreshLabel': {
    vi: 'Tự cập nhật',
    en: 'Refreshes',
  },
  'billing.balance.refreshValue': {
    vi: 'Mỗi 45 giây',
    en: 'Every 45 seconds',
  },
  'billing.balance.error.title': {
    vi: 'Không tải được số dư',
    en: "Couldn't load the balance",
  },
  'billing.balance.error.body': {
    vi: 'Có lỗi khi lấy số dư tín dụng. Hãy thử lại.',
    en: 'Something went wrong fetching your credit balance. Try again.',
  },
  'billing.balance.error.retry': {
    vi: 'Thử lại',
    en: 'Try again',
  },
  'billing.ledger.title': {
    vi: 'Lịch sử giao dịch',
    en: 'Transaction history',
  },
  'billing.ledger.description': {
    vi: 'Các lượt cấp, giữ, trừ và hoàn tín dụng gần đây.',
    en: 'Recent grants, holds, spends, and releases.',
  },
  'billing.ledger.empty.heading': {
    vi: 'Chưa có giao dịch',
    en: 'No transactions yet',
  },
  'billing.ledger.empty.body': {
    vi: 'Khi bạn dùng các tác vụ AI của Zero Mail, lịch sử tín dụng sẽ xuất hiện ở đây.',
    en: 'Your credit activity will show up here once you use Zero Mail AI actions.',
  },
  'billing.ledger.unavailable.heading': {
    vi: 'Lịch sử giao dịch chưa khả dụng',
    en: "Transaction history isn't available yet",
  },
  'billing.ledger.unavailable.body': {
    vi: 'Zero Mail đã có số dư tín dụng, nhưng danh sách giao dịch chi tiết sẽ được bật trong một bản cập nhật sau.',
    en: 'Zero Mail can show your credit balance now. Detailed transaction history will arrive in a later update.',
  },
  'billing.ledger.error.title': {
    vi: 'Không tải được lịch sử giao dịch',
    en: "Couldn't load transaction history",
  },
  'billing.ledger.error.body': {
    vi: 'Có lỗi khi lấy dữ liệu giao dịch. Hãy thử lại.',
    en: 'Something went wrong fetching transaction history. Try again.',
  },
  'billing.ledger.error.retry': {
    vi: 'Thử lại',
    en: 'Try again',
  },
  'billing.ledger.columns.timestamp': {
    vi: 'Thời gian',
    en: 'Time',
  },
  'billing.ledger.columns.type': {
    vi: 'Loại',
    en: 'Type',
  },
  'billing.ledger.columns.description': {
    vi: 'Mô tả',
    en: 'Description',
  },
  'billing.ledger.columns.amount': {
    vi: 'Tín dụng',
    en: 'Credits',
  },
  'billing.ledger.columns.balance': {
    vi: 'Số dư',
    en: 'Balance',
  },
  'billing.ledger.columns.reference': {
    vi: 'Tham chiếu',
    en: 'Reference',
  },
  'billing.ledger.type.topup': {
    vi: 'Cộng',
    en: 'Credit',
  },
  'billing.ledger.type.grant': {
    vi: 'Cấp',
    en: 'Grant',
  },
  'billing.ledger.type.reserve': {
    vi: 'Giữ',
    en: 'Reserve',
  },
  'billing.ledger.type.settle': {
    vi: 'Trừ',
    en: 'Settle',
  },
  'billing.ledger.type.release': {
    vi: 'Hoàn giữ',
    en: 'Release',
  },
  'billing.ledger.type.expire': {
    vi: 'Hết hạn',
    en: 'Expired',
  },
  'billing.ledger.type.adjustment': {
    vi: 'Điều chỉnh',
    en: 'Adjustment',
  },
  'billing.ledger.valueMissing': {
    vi: '—',
    en: '—',
  },
  'billing.copy.cta': {
    vi: 'Sao chép',
    en: 'Copy',
  },
  'billing.copy.done': {
    vi: 'Đã sao chép',
    en: 'Copied',
  },
  'billing.copy.aria': {
    vi: 'Sao chép {label}',
    en: 'Copy {label}',
  },
} as const;
