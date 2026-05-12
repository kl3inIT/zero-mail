import { api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type TriageShadowModeResponse = components['schemas']['TriageShadowModeResponse'];
export type ProtectedSenderResponse = components['schemas']['ProtectedSenderResponse'];
export type ProtectedSendersResponse = components['schemas']['ProtectedSendersResponse'];
export type SenderOptInResponse = components['schemas']['SenderOptInResponse'];
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
};

export type AuditLogUnavailablePage = {
  unavailable: true;
  entries: [];
  nextCursor: null;
};

export type AuditLogAvailablePage = {
  unavailable?: false;
  entries: AuditEntry[];
  nextCursor: string | null;
};

export type AuditLogPage = AuditLogUnavailablePage | AuditLogAvailablePage;

export type ShadowModeState = {
  enabled: boolean;
  readUnavailable: boolean;
};

function jsonHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json', ...xsrfHeader() };
}

function unsafeHeaders(): HeadersInit {
  return { ...xsrfHeader() };
}

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function setTriagePaused(paused: boolean): Promise<void> {
  const { error, response } = await api.PUT('/tenant/triage-pause', {
    body: { paused },
    headers: jsonHeaders(),
  });
  if (error || !response.ok)
    throw error ?? new Error(`/tenant/triage-pause failed: ${response.status}`);
}

// GAP: no backend triage-audit list endpoint as of 05A - see
// 05A-RESEARCH.md A4 / 05A-SPEC.md out-of-scope. Do not add an endpoint
// or regenerate schema.d.ts for this frontend degradation path.
export async function getAuditLog(options: { cursor?: string | null } = {}): Promise<AuditLogPage> {
  void options;
  return { unavailable: true, entries: [], nextCursor: null };
}

// GAP: the current backend schema exposes PATCH-only shadow-mode writes, but
// no shadow-mode read endpoint. The UI starts from a known false default and
// updates from the authoritative PATCH response after the user changes it.
export async function getShadowMode(): Promise<ShadowModeState> {
  return { enabled: false, readUnavailable: true };
}

export async function setShadowMode(enabled: boolean): Promise<ShadowModeState> {
  const result = await api.PATCH('/api/tenant/triage/shadow-mode', {
    body: { enabled },
    headers: jsonHeaders(),
  });
  const data = unwrap(result, `/api/tenant/triage/shadow-mode failed: ${result.response.status}`);
  return { enabled: data.enabled ?? enabled, readUnavailable: false };
}

export async function undoAuditEntry(auditId: string): Promise<UndoAuditResponse> {
  const result = await api.POST('/api/triage/audit/{auditId}/undo', {
    params: { path: { auditId } },
    headers: unsafeHeaders(),
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
    headers: unsafeHeaders(),
  });
  return unwrap(
    result,
    `/api/triage/sender-safety-net/${senderEmail}/opt-in failed: ${result.response.status}`,
  );
}
