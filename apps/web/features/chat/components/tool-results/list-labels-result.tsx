import { Tag } from 'lucide-react';

import { Badge } from '@/components/ui/badge';

import { EmptyHint } from './empty-hint';
import { asArray, getField } from './helpers';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function ListLabelsResult({ output }: { output: unknown }) {
  const labels =
    asArray<{ id?: string; name?: string; type?: string }>(getField(output, 'labels')) ?? [];
  return (
    <SubtleToolCollapsible title={`Danh sách nhãn · ${labels.length}`}>
      {labels.length === 0 ? (
        <EmptyHint>Không có nhãn nào.</EmptyHint>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {labels.map((label) => (
            <Badge key={label.id ?? label.name} variant="outline" className="gap-1 font-normal">
              <Tag className="size-3" />
              {label.name ?? label.id}
            </Badge>
          ))}
        </div>
      )}
    </SubtleToolCollapsible>
  );
}
