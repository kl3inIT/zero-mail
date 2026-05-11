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
  'errors.triage.safety_violation': {
    vi: 'Hành động triage bị chặn vì vi phạm ràng buộc an toàn.',
    en: 'The triage action was blocked by a safety constraint.',
  },
} as const;
