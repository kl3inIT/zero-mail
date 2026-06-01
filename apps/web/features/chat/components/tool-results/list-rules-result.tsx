import { EmptyHint } from './empty-hint';
import { asArray, getField, type RuleRow } from './helpers';
import { RuleCard } from './rule-card';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function ListRulesResult({ output }: { output: unknown }) {
  const rules = asArray<RuleRow>(getField(output, 'rules')) ?? [];
  return (
    <SubtleToolCollapsible
      title={`Danh sách quy tắc · ${rules.length}`}
      defaultOpen={rules.length > 0 && rules.length <= 5}
    >
      {rules.length === 0 ? (
        <EmptyHint>Chưa có quy tắc nào.</EmptyHint>
      ) : (
        <div className="space-y-2">
          {rules.map((rule, index) => (
            <RuleCard key={rule.ruleId ?? index} rule={rule} />
          ))}
        </div>
      )}
    </SubtleToolCollapsible>
  );
}
