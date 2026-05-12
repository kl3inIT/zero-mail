export const triageKeys = {
  all: ['triage'] as const,
  pauseState: () => [...triageKeys.all, 'pause-state'] as const,
  auditLog: (filters?: Record<string, unknown>) =>
    filters
      ? ([...triageKeys.all, 'audit-log', filters] as const)
      : ([...triageKeys.all, 'audit-log'] as const),
  shadowMode: () => [...triageKeys.all, 'shadow-mode'] as const,
  senderSafetyNet: () => [...triageKeys.all, 'sender-safety-net'] as const,
} as const;
