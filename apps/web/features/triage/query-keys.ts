export const triageKeys = {
  all: ['triage'] as const,
  auditLog: (filters?: Record<string, unknown>) =>
    filters
      ? ([...triageKeys.all, 'audit-log', filters] as const)
      : ([...triageKeys.all, 'audit-log'] as const),
  senderSafetyNet: () => [...triageKeys.all, 'sender-safety-net'] as const,
} as const;
