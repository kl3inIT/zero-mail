import { api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type ProtectedSenderResponse = components['schemas']['ProtectedSenderResponse'];
export type ProtectedSendersResponse = components['schemas']['ProtectedSendersResponse'];
export type SenderOptInResponse = components['schemas']['ProtectedSenderResponse'];
export type UndoAuditResponse = components['schemas']['UndoAuditResponse'];

export type AuditMessageRef = {
  subject?: string;
  sender?: string;
  gmailMessageId?: string;
};

export type AuditEntry = {
  id: string;
  timestamp: string;
  action: string;
  actionLabel: string;
  ruleName: string;
  reason: string;
  inverseAction: string;
  messageRef?: AuditMessageRef;
  undoableUntil: string;
  undone?: boolean;
  gmailThreadId?: string;
  draftId?: string;
  decisionState?: string;
  blockedBySafetyNetPattern?: string | null;
  sourceMailboxId?: string;
  sourceMailboxEmail?: string;
  executingMailboxId?: string;
  executingMailboxEmail?: string;
};

export type AuditLogPage = {
  entries: AuditEntry[];
  nextCursor: string | null;
};

export type AuditLogFilters = {
  action?: string;
  since?: string;
  until?: string;
};

export type AuditLogOptions = AuditLogFilters & {
  cursor?: string | null;
  limit?: number;
};

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

function auditActionLabel(action: string): string {
  return action
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
}

function inverseActionLabel(action: string): string {
  const normalizedAction = action.toLowerCase();
  if (normalizedAction.includes('archive')) return 'Move the message back to Inbox.';
  if (normalizedAction.includes('label')) return 'Remove the applied label.';
  if (normalizedAction.includes('draft')) return 'Delete the saved draft.';
  return 'Reverse this action.';
}

function mapAuditEntry(row: components['schemas']['AuditEntryResponse']): AuditEntry | null {
  if (!row.auditId) return null;
  const timestamp = row.createdAt ?? new Date(0).toISOString();
  const action = row.action ?? 'unknown';
  const mailboxProvenance = row as components['schemas']['AuditEntryResponse'] & {
    sourceMailboxId?: string;
    sourceMailboxEmail?: string;
    executingMailboxId?: string;
    executingMailboxEmail?: string;
  };

  return {
    id: row.auditId,
    timestamp,
    action,
    actionLabel: auditActionLabel(action),
    ruleName: row.ruleName ?? '',
    reason: row.reason ?? '',
    inverseAction: inverseActionLabel(action),
    messageRef: {
      gmailMessageId: row.gmailMessageId,
      subject: row.subject ?? undefined,
      sender: row.senderEmail ?? undefined,
    },
    undoableUntil: row.undoableUntil ?? new Date(0).toISOString(),
    undone: row.decisionState === 'REVERTED',
    gmailThreadId: row.gmailThreadId,
    draftId: row.draftId ?? undefined,
    decisionState: row.decisionState,
    blockedBySafetyNetPattern: row.blockedBySafetyNetPattern ?? null,
    sourceMailboxId: mailboxProvenance.sourceMailboxId,
    sourceMailboxEmail: mailboxProvenance.sourceMailboxEmail,
    executingMailboxId: mailboxProvenance.executingMailboxId,
    executingMailboxEmail: mailboxProvenance.executingMailboxEmail,
  };
}

export async function setTriagePaused(paused: boolean): Promise<void> {
  const { error, response } = await api.PUT('/api/tenant/triage-pause', {
    body: { paused },
  });
  if (error || !response.ok)
    throw error ?? new Error(`/api/tenant/triage-pause failed: ${response.status}`);
}

export async function getAuditLog(options: AuditLogOptions = {}): Promise<AuditLogPage> {
  const result = await api.GET('/api/triage/audit', {
    params: {
      query: {
        action: options.action,
        cursor: options.cursor ?? undefined,
        limit: options.limit,
        since: options.since,
        until: options.until,
      },
    },
  });
  const data = unwrap(result, `/api/triage/audit failed: ${result.response.status}`);
  return {
    entries: (data.items ?? [])
      .map(mapAuditEntry)
      .filter((entry): entry is AuditEntry => entry !== null),
    nextCursor: data.nextCursor ?? null,
  };
}

export async function undoAuditEntry(auditId: string): Promise<UndoAuditResponse> {
  const result = await api.POST('/api/triage/audit/{auditId}/undo', {
    params: { path: { auditId } },
  });
  return unwrap(result, `/api/triage/audit/${auditId}/undo failed: ${result.response.status}`);
}

export async function getProtectedSenders(): Promise<ProtectedSendersResponse> {
  const result = await api.GET('/api/triage/sender-safety-net', {});
  return unwrap(result, `/api/triage/sender-safety-net failed: ${result.response.status}`);
}

export async function optInSender(senderEmail: string): Promise<SenderOptInResponse> {
  const result = await api.POST('/api/triage/sender-safety-net/{senderEmail}/opt-in', {
    params: { path: { senderEmail } },
  });
  return unwrap(
    result,
    `/api/triage/sender-safety-net/${senderEmail}/opt-in failed: ${result.response.status}`,
  );
}

export async function deleteProtectedSender(id: string): Promise<void> {
  const result = await api.DELETE('/api/triage/sender-safety-net/{id}', {
    params: { path: { id } },
  });
  if (result.error || !result.response.ok) {
    throw (
      result.error ??
      new Error(`/api/triage/sender-safety-net/${id} failed: ${result.response.status}`)
    );
  }
}
