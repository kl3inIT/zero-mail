export const mailboxQueryKeys = {
  all: ['mailbox'] as const,
  list: () => [...mailboxQueryKeys.all, 'list'] as const,
  active: () => [...mailboxQueryKeys.all, 'active'] as const,
} as const;
