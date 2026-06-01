import { EmailRowsList } from './email-rows-list';
import { asArray, asString, getField, type EmailRowData } from './helpers';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';
import { ToolDetailRow } from './tool-detail-row';

export function SearchInboxResult({ input, output }: { input: unknown; output: unknown }) {
  const query = asString(getField(input, 'query'));
  const messages = asArray<EmailRowData>(getField(output, 'messages')) ?? [];
  return (
    <SubtleToolCollapsible
      title={`Tìm trong inbox · ${messages.length} kết quả`}
      defaultOpen={messages.length > 0}
    >
      {query && (
        <ToolDetailRow
          label="Truy vấn"
          value={<span className="font-mono text-xs">{query}</span>}
        />
      )}
      <EmailRowsList emails={messages} />
    </SubtleToolCollapsible>
  );
}
