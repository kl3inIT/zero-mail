import { asString, formatRelativeDate, getField } from './helpers';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';
import { ToolDetailRow } from './tool-detail-row';

export function GetSenderSafetyEntryResult({ input, output }: { input: unknown; output: unknown }) {
  const senderEmail = asString(getField(input, 'senderEmail'));
  const mode = asString(getField(output, 'mode'));
  const addedAt = asString(getField(output, 'addedAt'));
  const modeLabel =
    mode === 'opted_in'
      ? 'Đã đồng ý nhận'
      : mode === 'protected'
        ? 'Được bảo vệ (safety net)'
        : mode === 'not_found'
          ? 'Không có trong danh sách'
          : (mode ?? 'Không rõ');
  return (
    <SubtleToolCollapsible title="Trạng thái safety-net" defaultOpen>
      {senderEmail && <ToolDetailRow label="Người gửi" value={senderEmail} />}
      <ToolDetailRow label="Trạng thái" value={modeLabel} />
      {addedAt && <ToolDetailRow label="Thêm vào" value={formatRelativeDate(addedAt)} />}
    </SubtleToolCollapsible>
  );
}
