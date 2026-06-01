import { MailX } from 'lucide-react';

import { asString, getField } from './helpers';
import { StatusLine } from './status-line';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function DisableRuleResult({ input }: { input: unknown }) {
  const ruleId = asString(getField(input, 'ruleId'));
  return (
    <SubtleToolCollapsible title="Đã tắt quy tắc">
      <StatusLine icon={MailX}>
        Quy tắc {ruleId ? <code className="text-[11px]">{ruleId}</code> : ''} đã chuyển sang tắt.
      </StatusLine>
    </SubtleToolCollapsible>
  );
}
