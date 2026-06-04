export const inboxKeys = {
  all: ['inbox'] as const,
  pages: () => [...inboxKeys.all, 'pages'] as const,
  detail: (gmailMessageId: string | null) => [...inboxKeys.all, 'detail', gmailMessageId] as const,
  thread: (gmailThreadId: string | null) => [...inboxKeys.all, 'thread', gmailThreadId] as const,
  composerDraft: (gmailThreadId: string | null) =>
    [...inboxKeys.all, 'composer-draft', gmailThreadId] as const,
  draft: (gmailDraftId: string | null) => [...inboxKeys.all, 'draft', gmailDraftId] as const,
};
