export const needsReplyMessages = {
  'errors.draft.generation.in_flight': {
    vi: 'Đang tạo bản nháp cho luồng email này.',
    en: 'A draft is already being generated for this thread.',
  },
  'errors.draft.generation.failed': {
    vi: 'Chưa thể tạo bản nháp. Hãy thử lại sau.',
    en: "Couldn't generate a draft. Try again in a moment.",
  },
  'errors.pagination.invalid_cursor': {
    vi: 'Đã xảy ra lỗi. Hãy thử lại.',
    en: 'Something went wrong. Try again.',
  },
} as const;
