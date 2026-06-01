import { EmptyHint } from './empty-hint';
import { getField, type RuleRow } from './helpers';
import { RuleCard } from './rule-card';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function GetRuleResult({ output }: { output: unknown }) {
  const rule = getField<RuleRow>(output, 'rule');
  return (
    <SubtleToolCollapsible title="Xem quy tắc" defaultOpen>
      {rule ? <RuleCard rule={rule} /> : <EmptyHint>Không tìm thấy quy tắc.</EmptyHint>}
    </SubtleToolCollapsible>
  );
}
