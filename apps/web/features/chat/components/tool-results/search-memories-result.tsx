import { EmptyHint } from './empty-hint';
import { asArray, asString, formatRelativeDate, getField } from './helpers';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';
import { ToolDetailRow } from './tool-detail-row';

export function SearchMemoriesResult({ input, output }: { input: unknown; output: unknown }) {
  const query = asString(getField(input, 'query'));
  const memories =
    asArray<{ id?: string; content?: string; createdAt?: string }>(getField(output, 'memories')) ??
    [];
  return (
    <SubtleToolCollapsible
      title={`Tìm kiến thức · ${memories.length}`}
      defaultOpen={memories.length > 0}
    >
      {query && (
        <ToolDetailRow
          label="Truy vấn"
          value={<span className="font-mono text-xs">{query}</span>}
        />
      )}
      {memories.length === 0 ? (
        <EmptyHint>Không tìm thấy kiến thức nào khớp.</EmptyHint>
      ) : (
        <ul className="divide-border divide-y">
          {memories.map((memory, index) => (
            <li key={memory.id ?? index} className="space-y-1 py-2 first:pt-0 last:pb-0">
              <p className="text-foreground text-sm whitespace-pre-wrap">{memory.content}</p>
              {memory.createdAt && (
                <p className="text-muted-foreground text-[11px]">
                  {formatRelativeDate(memory.createdAt)}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </SubtleToolCollapsible>
  );
}
