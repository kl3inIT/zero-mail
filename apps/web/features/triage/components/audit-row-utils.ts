import type { AuditEntry } from '@/features/triage/api/triage-api';

const auditDateFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: '2-digit',
});

export function isUndoAvailable(entry: AuditEntry, now: Date): boolean {
  return !entry.undone && new Date(entry.undoableUntil).getTime() > now.getTime();
}

export function shouldShowUndoBoundary(entries: AuditEntry[], index: number, now: Date): boolean {
  if (index === 0) return false;
  return isUndoAvailable(entries[index - 1], now) && !isUndoAvailable(entries[index], now);
}

export function shouldShowDraftAction(entry: AuditEntry): entry is AuditEntry & {
  gmailThreadId: string;
} {
  const normalizedAction = entry.action.toLowerCase().replace(/[-\s]+/g, '_');
  return normalizedAction === 'save_draft' && Boolean(entry.gmailThreadId);
}

export function formatAuditTimestamp(timestamp: string, now: Date = new Date()): string {
  const entryDate = new Date(timestamp);
  const deltaMs = now.getTime() - entryDate.getTime();
  const deltaMinutes = Math.round(deltaMs / 60_000);
  if (deltaMinutes < 1) return 'vừa xong';
  if (deltaMinutes < 60) return `${deltaMinutes} phút trước`;
  const deltaHours = Math.round(deltaMinutes / 60);
  if (deltaHours < 24) return `${deltaHours} giờ trước`;
  const deltaDays = Math.round(deltaHours / 24);
  if (deltaDays < 7) return `${deltaDays} ngày trước`;
  return auditDateFormatter.format(entryDate);
}
