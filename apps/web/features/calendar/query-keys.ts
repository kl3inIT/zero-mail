export const calendarQueryKeys = {
  all: ['calendar'] as const,
  connections: (mailboxId: string) =>
    [...calendarQueryKeys.all, 'mailbox', mailboxId, 'connections'] as const,
} as const;
