import { MailX } from 'lucide-react';

import { asString, getField } from './helpers';
import { StatusLine } from './status-line';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function ArchiveThreadResult({ input, output }: { input: unknown; output: unknown }) {
  const threadId = asString(getField(input, 'threadId')) ?? asString(getField(output, 'thread_id'));
  return (
    <SubtleToolCollapsible title="Đã lưu trữ chuỗi">
      <StatusLine icon={MailX}>
        Lưu trữ chuỗi {threadId ? <code className="text-[11px]">{threadId}</code> : ''} (đã bỏ khỏi
        Inbox).
      </StatusLine>
    </SubtleToolCollapsible>
  );
}
