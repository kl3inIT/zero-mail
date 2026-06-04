import { Tag } from 'lucide-react';

import { asString, getField } from './helpers';
import { InlineEmailCard } from './inline-email-card';
import { StatusLine } from './status-line';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function ApplyLabelResult({ input, output }: { input: unknown; output: unknown }) {
  const labelName =
    asString(getField(input, 'labelName')) ?? asString(getField(output, 'label_id')) ?? 'nhãn';
  const messageId =
    asString(getField(input, 'messageId')) ?? asString(getField(output, 'message_id'));
  return (
    <SubtleToolCollapsible title={`Đã gắn nhãn "${labelName}"`}>
      <StatusLine icon={Tag}>
        Gắn nhãn <strong>{labelName}</strong>
        {messageId ? ' cho email:' : '.'}
      </StatusLine>
      {messageId && (
        <InlineEmailCard
          email={{ messageId, threadId: messageId }}
          fallbackTitle="Email đã gắn nhãn"
        />
      )}
    </SubtleToolCollapsible>
  );
}
