import { useTranslations } from 'next-intl';

import { Textarea } from '@/components/ui/textarea';
import {
  type PreviewCardAction,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';

export function SaveMemoryBody({
  action,
  editing,
  onOverrideChange,
}: {
  action: PreviewCardAction;
  editing: boolean;
  onOverrideChange: (key: string, value: string) => void;
}) {
  const t = useTranslations('chat.preview');
  const content = textValue(action.input.content);

  return (
    <div className="grid gap-2 text-sm">
      <p className="text-muted-foreground text-xs font-medium uppercase">{t('memoryContent')}</p>
      {editing ? (
        <Textarea
          className="min-h-28"
          defaultValue={content}
          onChange={(event) => onOverrideChange('content', event.currentTarget.value)}
        />
      ) : (
        <p className="bg-muted rounded-md p-3 leading-relaxed">{content}</p>
      )}
    </div>
  );
}
