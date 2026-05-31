import { asString, getField } from './helpers';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';
import { ToolDetailRow } from './tool-detail-row';

export function SaveDraftResult({ input, output }: { input: unknown; output: unknown }) {
  const to = asString(getField(input, 'to'));
  const subject = asString(getField(input, 'subject'));
  const body = asString(getField(input, 'body'));
  const draftId = asString(getField(output, 'draft_id'));
  return (
    <SubtleToolCollapsible title="Đã lưu nháp" defaultOpen>
      {to && <ToolDetailRow label="Đến" value={to} />}
      {subject && <ToolDetailRow label="Chủ đề" value={subject} />}
      {body && (
        <div className="border-border bg-muted/30 max-h-48 overflow-y-auto rounded border p-2 text-xs whitespace-pre-wrap">
          {body}
        </div>
      )}
      {draftId && (
        <ToolDetailRow label="Draft ID" value={<code className="text-[11px]">{draftId}</code>} />
      )}
    </SubtleToolCollapsible>
  );
}
