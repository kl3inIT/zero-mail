import { TagsIcon } from 'lucide-react';

import { asString, getField } from './helpers';
import { StatusLine } from './status-line';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function RemoveLabelResult({ input, output }: { input: unknown; output: unknown }) {
  const labelName =
    asString(getField(input, 'labelName')) ?? asString(getField(output, 'label_id')) ?? 'nhãn';
  const messageId =
    asString(getField(input, 'messageId')) ?? asString(getField(output, 'message_id'));
  return (
    <SubtleToolCollapsible title={`Đã gỡ nhãn "${labelName}"`}>
      <StatusLine icon={TagsIcon}>
        Gỡ nhãn <strong>{labelName}</strong> khỏi email{' '}
        {messageId ? <code className="text-[11px]">{messageId}</code> : ''}.
      </StatusLine>
    </SubtleToolCollapsible>
  );
}
