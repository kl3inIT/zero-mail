import { gmailMessageUrl } from './gmail-url';
import { asArray, asString, formatRelativeDate, getField } from './helpers';
import { OpenInGmailLink } from './open-in-gmail-link';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';
import { ToolDetailRow } from './tool-detail-row';

export function GetMessageResult({ input, output }: { input: unknown; output: unknown }) {
  const messageId =
    asString(getField(input, 'messageId')) ?? asString(getField(output, 'messageId'));
  const subject = asString(getField(output, 'subject'));
  const from = asString(getField(output, 'from'));
  const to = asArray<string>(getField(output, 'to'));
  const cc = asArray<string>(getField(output, 'cc'));
  const date = asString(getField(output, 'date'));
  const bodyText = asString(getField(output, 'bodyText'));
  return (
    <SubtleToolCollapsible title="Đọc email" defaultOpen>
      {subject && <ToolDetailRow label="Chủ đề" value={subject} />}
      {from && <ToolDetailRow label="Từ" value={from} />}
      {to && to.length > 0 && <ToolDetailRow label="Đến" value={to.join(', ')} />}
      {cc && cc.length > 0 && <ToolDetailRow label="CC" value={cc.join(', ')} />}
      {date && <ToolDetailRow label="Thời gian" value={formatRelativeDate(date)} />}
      {bodyText && (
        <div className="border-border bg-muted/30 max-h-64 overflow-y-auto rounded border p-2 text-xs whitespace-pre-wrap">
          {bodyText}
        </div>
      )}
      {messageId && <OpenInGmailLink href={gmailMessageUrl(messageId)} />}
    </SubtleToolCollapsible>
  );
}
