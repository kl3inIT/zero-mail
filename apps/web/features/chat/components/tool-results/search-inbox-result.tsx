import { asArray, asString, getField, type EmailRowData } from './helpers';
import { InlineEmailCardList } from './inline-email-card';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function SearchInboxResult({ input, output }: { input: unknown; output: unknown }) {
  const query = asString(getField(input, 'query'));
  const messages = asArray<EmailRowData>(getField(output, 'messages')) ?? [];
  return (
    <SubtleToolCollapsible
      title={`Tìm trong inbox · ${messages.length} kết quả`}
      defaultOpen={messages.length > 0}
    >
      {query && (
        <p className="text-muted-foreground text-xs">
          Truy vấn: <span className="font-mono">{query}</span>
        </p>
      )}
      <InlineEmailCardList emails={messages} />
    </SubtleToolCollapsible>
  );
}
