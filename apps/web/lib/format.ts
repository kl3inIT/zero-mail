// Locale-aware Intl wrappers shared across features so credit counts and timestamps
// render with the same shape everywhere.

export function formatCredits(value: number, locale?: string): string {
  return new Intl.NumberFormat(locale).format(value);
}

export function formatDateTime(value: string, locale?: string): string {
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return value;
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(timestamp);
}
