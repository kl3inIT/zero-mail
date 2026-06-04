import { MailX } from 'lucide-react';

import { gmailThreadUrl } from './gmail-url';
import { asString, getField } from './helpers';
import { OpenInGmailLink } from './open-in-gmail-link';
import { StatusLine } from './status-line';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function ArchiveThreadResult({ input, output }: { input: unknown; output: unknown }) {
  const threadId = asString(getField(input, 'threadId')) ?? asString(getField(output, 'thread_id'));
  return (
    <SubtleToolCollapsible title="Đã lưu trữ chuỗi">
      <StatusLine icon={MailX}>Đã lưu chuỗi này (bỏ khỏi Inbox).</StatusLine>
      {threadId && <OpenInGmailLink href={gmailThreadUrl(threadId)} label="Mở chuỗi trong Gmail" />}
    </SubtleToolCollapsible>
  );
}
