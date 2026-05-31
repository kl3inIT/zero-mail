import { asArray, asString, formatRelativeDate, getField } from './helpers';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';
import { ToolDetailRow } from './tool-detail-row';

export function GetThreadResult({ input, output }: { input: unknown; output: unknown }) {
  const threadId = asString(getField(input, 'threadId')) ?? asString(getField(output, 'threadId'));
  const participants = asArray<string>(getField(output, 'participantList')) ?? [];
  const messageIds = asArray<string>(getField(output, 'messageIds')) ?? [];
  const lastActivityAt = asString(getField(output, 'lastActivityAt'));
  return (
    <SubtleToolCollapsible title={`Xem chuỗi · ${messageIds.length} email`} defaultOpen>
      {threadId && (
        <ToolDetailRow label="Thread" value={<code className="text-xs">{threadId}</code>} />
      )}
      {participants.length > 0 && (
        <ToolDetailRow label="Người tham gia" value={participants.join(', ')} />
      )}
      {lastActivityAt && (
        <ToolDetailRow label="Hoạt động mới" value={formatRelativeDate(lastActivityAt)} />
      )}
    </SubtleToolCollapsible>
  );
}
