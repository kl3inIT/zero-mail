import { Badge } from '@/components/ui/badge';

import { asBool, asString, getField } from './helpers';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';
import { ToolDetailRow } from './tool-detail-row';

export function UpdateRuleResult({ input, output }: { input: unknown; output: unknown }) {
  const displayName = asString(getField(input, 'displayName')) ?? 'Quy tắc';
  const enabled = asBool(getField(output, 'enabled'));
  return (
    <SubtleToolCollapsible title={`Đã cập nhật "${displayName}"`} defaultOpen>
      <ToolDetailRow label="Tên" value={displayName} />
      <ToolDetailRow
        label="Trạng thái"
        value={
          <Badge variant={enabled ? 'default' : 'secondary'} className="text-[10px]">
            {enabled ? 'Đang bật' : 'Đang tắt'}
          </Badge>
        }
      />
    </SubtleToolCollapsible>
  );
}
