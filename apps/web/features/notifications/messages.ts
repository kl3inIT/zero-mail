export const notificationsMessages = {
  'settings.notifications.title': {
    vi: 'Thông báo',
    en: 'Notifications',
  },
  'settings.notifications.description': {
    vi: 'Quản lý email tóm tắt hàng ngày của bạn.',
    en: 'Manage your daily summary email.',
  },
  'settings.notifications.toggle.label': {
    vi: 'Email tóm tắt hàng ngày',
    en: 'Daily digest email',
  },
  'settings.notifications.toggle.helperOn': {
    vi: 'Bạn sẽ nhận email tóm tắt mỗi ngày vào giờ đã chọn.',
    en: "You'll receive a summary email each day at the chosen hour.",
  },
  'settings.notifications.toggle.helperOff': {
    vi: 'Đã tắt — bạn sẽ không nhận email tóm tắt nào.',
    en: "Off — you'll receive no summary emails.",
  },
  'settings.notifications.sendHour.label': {
    vi: 'Giờ gửi (giờ địa phương)',
    en: 'Send hour (local time)',
  },
  'settings.notifications.sendHour.helper': {
    vi: 'Email tóm tắt được gửi khoảng giờ này theo múi giờ của bạn.',
    en: 'The digest is sent around this hour in your time zone.',
  },
  'settings.notifications.sendHour.downtimeNote': {
    vi: 'Nếu trình gửi digest gặp sự cố đúng giờ bạn chọn, bản digest hôm đó sẽ bị bỏ qua — không có cơ chế gửi bù. Hãy chọn giờ mà hệ thống hoạt động ổn định.',
    en: "If the digest worker is offline during your selected hour, that day's digest is skipped — there is no automatic catch-up. Choose an hour when the system is reliably running.",
  },
  'settings.notifications.timeZone.label': {
    vi: 'Múi giờ: {tz}',
    en: 'Time zone: {tz}',
  },
  'settings.notifications.timeZone.tooltip': {
    vi: 'Đặt tự động. Hỗ trợ nhiều múi giờ sẽ có sau.',
    en: 'Set automatically. Multi-time-zone support is coming.',
  },
  'settings.notifications.toast.savedTitle': {
    vi: 'Đã lưu',
    en: 'Saved',
  },
  'settings.notifications.toast.errorTitle': {
    vi: 'Không lưu được — Thử lại',
    en: "Couldn't save — Try again",
  },
  'settings.notifications.toast.retry': {
    vi: 'Thử lại',
    en: 'Retry',
  },
} as const;
