// Client-side Gmail deep-links. Gmail's hash router opens the conversation that
// contains a given message or thread id under the "#all" view (works for
// archived mail too, unlike "#inbox"). The chat tool outputs already carry
// messageId/threadId, so we can deep-link without an extra round-trip.
const GMAIL_ALL_VIEW = 'https://mail.google.com/mail/u/0/#all';

export function gmailThreadUrl(threadId: string): string {
  return `${GMAIL_ALL_VIEW}/${encodeURIComponent(threadId)}`;
}

export function gmailMessageUrl(messageId: string): string {
  return `${GMAIL_ALL_VIEW}/${encodeURIComponent(messageId)}`;
}

// Gmail does not expose a reliable per-draft deep-link by API draft id, so we
// open the Drafts folder where the freshly saved draft appears at the top.
export const GMAIL_DRAFTS_URL = 'https://mail.google.com/mail/u/0/#drafts';
