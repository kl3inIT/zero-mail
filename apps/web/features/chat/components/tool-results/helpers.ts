type Unknown = Record<string, unknown>;

export function getField<T>(source: unknown, field: string): T | undefined {
  if (typeof source === 'object' && source !== null && field in source) {
    return (source as Unknown)[field] as T;
  }
  return undefined;
}

export function asString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

export function asArray<T = unknown>(value: unknown): T[] | undefined {
  return Array.isArray(value) ? (value as T[]) : undefined;
}

export function asBool(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined;
}

export function formatRelativeDate(isoDate: string | undefined): string {
  if (!isoDate) return '';
  const parsed = new Date(isoDate);
  if (Number.isNaN(parsed.getTime())) return '';
  const now = Date.now();
  const diffMs = now - parsed.getTime();
  const diffMinutes = Math.round(diffMs / 60_000);
  if (diffMinutes < 1) return 'vừa xong';
  if (diffMinutes < 60) return `${diffMinutes} phút trước`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} giờ trước`;
  const diffDays = Math.round(diffHours / 24);
  if (diffDays < 7) return `${diffDays} ngày trước`;
  return parsed.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

export type EmailRowData = {
  messageId: string;
  threadId: string;
  subject?: string;
  from?: string;
  snippet?: string;
  date?: string;
  isUnread?: boolean;
  hasAttachment?: boolean;
};

export type RuleRow = {
  ruleId?: string;
  displayName?: string;
  sourceText?: string;
  enabled?: boolean;
  matcher?: string;
  actions?: string;
};
