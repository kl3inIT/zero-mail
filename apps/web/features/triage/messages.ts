export const triageMessages = {
  'errors.triage.undo.already_done': {
    vi: 'Hành động email này đã được hoàn tác hoặc không còn ở trạng thái có thể hoàn tác.',
    en: 'This email action has already been undone or is no longer undoable.',
  },
  'errors.triage.undo.expired': {
    vi: 'Chỉ có thể hoàn tác hành động triage trong vòng 30 ngày sau khi áp dụng.',
    en: 'Triage actions can only be undone within 30 days after they are applied.',
  },
  'errors.triage.undo.unsupported_action': {
    vi: 'Zero Mail không thể hoàn tác loại hành động triage này một cách an toàn.',
    en: 'Zero Mail cannot safely undo this triage action type.',
  },
  'errors.triage.undo.write_failed': {
    vi: 'Chưa thể hoàn tác hành động email lúc này. Hãy thử lại sau.',
    en: 'This email action could not be undone right now. Try again later.',
  },
  'errors.triage.audit.not_found': {
    vi: 'Không tìm thấy bản ghi triage này. Hãy tải lại danh sách.',
    en: 'This triage audit entry could not be found. Reload the list.',
  },
  'errors.triage.safety_violation': {
    vi: 'Hành động triage bị chặn vì vi phạm ràng buộc an toàn.',
    en: 'The triage action was blocked by a safety constraint.',
  },
} as const;
