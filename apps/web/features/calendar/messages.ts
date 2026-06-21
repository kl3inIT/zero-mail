export const calendarMessages = {
  'calendar.title': {
    vi: 'Lịch',
    en: 'Calendar',
  },
  'calendar.subtitle': {
    vi: 'Kết nối Google Calendar để AI gợi ý khung giờ rảnh và đặt lịch giúp bạn.',
    en: 'Connect Google Calendar to let AI suggest free slots and schedule meetings for you.',
  },
  'calendar.empty.heading': {
    vi: 'Chưa kết nối Google Calendar',
    en: 'No Google Calendar connected',
  },
  'calendar.empty.body': {
    vi: 'Kết nối Google Calendar để Zero Mail biết khung giờ rảnh của bạn, gợi ý thời gian họp và soạn email với ngữ cảnh lịch.',
    en: 'Connect Google Calendar so Zero Mail knows your free time, suggests meeting slots, and drafts emails with calendar context.',
  },
  'calendar.empty.connectCta': {
    vi: 'Kết nối Google Calendar',
    en: 'Connect Google Calendar',
  },
  'calendar.card.connectedSince': {
    vi: 'Đã kết nối từ {date}',
    en: 'Connected since {date}',
  },
  'calendar.card.statusConnected': {
    vi: 'Đã kết nối',
    en: 'Connected',
  },
  'calendar.card.statusDisconnected': {
    vi: 'Đã ngắt',
    en: 'Disconnected',
  },
  'calendar.card.menuLabel': {
    vi: 'Tuỳ chọn kết nối',
    en: 'Connection options',
  },
  'calendar.card.disconnect': {
    vi: 'Ngắt kết nối',
    en: 'Disconnect',
  },
  'calendar.list.heading': {
    vi: 'Lịch con ({count})',
    en: 'Sub-calendars ({count})',
  },
  'calendar.list.empty': {
    vi: 'Chưa có lịch con nào được phơi ra.',
    en: 'No sub-calendars exposed yet.',
  },
  'calendar.list.timezone': {
    vi: 'Múi giờ: {tz}',
    en: 'Time zone: {tz}',
  },
  'calendar.list.toggleLabel': {
    vi: 'Bật / tắt lịch {name}',
    en: 'Enable / disable {name}',
  },
  'calendar.roles.heading': {
    vi: 'Vai trò theo lịch',
    en: 'Per-role assignments',
  },
  'calendar.roles.intro': {
    vi: 'Chọn lịch nào được dùng để kiểm tra xung đột, tạo sự kiện và làm nguồn cho meeting brief — riêng cho hộp thư này.',
    en: 'Pick which calendars are used to check conflicts, create events, and seed meeting briefs — for this mailbox only.',
  },
  'calendar.roles.freebusyLabel': {
    vi: 'Kiểm tra xung đột bằng',
    en: 'Check for conflicts using',
  },
  'calendar.roles.freebusyHelper': {
    vi: 'Chọn nhiều lịch — Zero Mail gom rảnh/bận từ tất cả các lịch bạn chọn.',
    en: 'Multi-select — Zero Mail merges free/busy from every calendar you pick.',
  },
  'calendar.roles.eventWriteLabel': {
    vi: 'Tạo sự kiện vào',
    en: 'Add events to',
  },
  'calendar.roles.eventWriteHelper': {
    vi: 'Chọn duy nhất một lịch — sự kiện do Zero Mail tạo sẽ rơi vào đây.',
    en: 'Single-select — events Zero Mail creates land here.',
  },
  'calendar.roles.briefSourceLabel': {
    vi: 'Nguồn meeting brief',
    en: 'Use for meeting briefs',
  },
  'calendar.roles.briefSourceHelper': {
    vi: 'Chọn duy nhất một lịch — meeting brief sẽ kéo dữ liệu từ đây.',
    en: 'Single-select — meeting briefs pull data from here.',
  },
  'calendar.roles.placeholderNone': {
    vi: 'Chưa chọn',
    en: 'Not set',
  },
  'calendar.roles.save': {
    vi: 'Lưu vai trò',
    en: 'Save assignments',
  },
  'calendar.actions.disconnect.success': {
    vi: 'Đã ngắt kết nối lịch',
    en: 'Calendar disconnected',
  },
  'calendar.actions.disconnect.error': {
    vi: 'Không ngắt được — thử lại',
    en: 'Could not disconnect — try again',
  },
  'calendar.actions.toggle.success': {
    vi: 'Đã cập nhật',
    en: 'Updated',
  },
  'calendar.actions.toggle.error': {
    vi: 'Không cập nhật được',
    en: 'Could not update',
  },
  'calendar.actions.preference.success': {
    vi: 'Đã lưu vai trò lịch',
    en: 'Calendar roles saved',
  },
  'calendar.actions.preference.error': {
    vi: 'Không lưu được vai trò lịch',
    en: 'Could not save calendar roles',
  },
  'calendar.connectIntent.error': {
    vi: 'Không bắt đầu được — thử lại',
    en: 'Could not start connect flow',
  },
  'calendar.error.loadFailed': {
    vi: 'Không tải được danh sách lịch',
    en: 'Could not load calendars',
  },
} as const;
