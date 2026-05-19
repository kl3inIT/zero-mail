import { useTranslations } from 'next-intl';

import { Textarea } from '@/components/ui/textarea';
import {
  type PreviewCardAction,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';

export function UpdatePersonalInstructionsBody({
  action,
  editing,
  onOverrideChange,
}: {
  action: PreviewCardAction;
  editing: boolean;
  onOverrideChange: (key: string, value: string) => void;
}) {
  const t = useTranslations('chat.preview');
  const before = textValue(action.input.before ?? action.input.currentPersonalInstructions);
  const after = textValue(action.input.personalInstructions);

  return (
    <div className="grid gap-3 text-sm">
      <p className="text-amber font-medium">{t('overwriteNotice')}</p>
      <div className="grid gap-3 md:grid-cols-2">
        <div>
          <p className="text-muted-foreground text-xs font-medium uppercase">
            {t('instructionsBefore')}
          </p>
          <p className="bg-muted min-h-24 rounded-md p-3 whitespace-pre-wrap">{before || '—'}</p>
        </div>
        <div>
          <p className="text-muted-foreground text-xs font-medium uppercase">
            {t('instructionsAfter')}
          </p>
          {editing ? (
            <Textarea
              className="min-h-24"
              defaultValue={after}
              onChange={(event) =>
                onOverrideChange('personalInstructions', event.currentTarget.value)
              }
            />
          ) : (
            <p className="bg-muted min-h-24 rounded-md p-3 whitespace-pre-wrap">{after}</p>
          )}
        </div>
      </div>
    </div>
  );
}
