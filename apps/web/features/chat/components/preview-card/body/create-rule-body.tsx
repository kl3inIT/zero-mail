import { useTranslations } from 'next-intl';

import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import {
  type PreviewCardAction,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';
import { ActionBadges } from '@/features/chat/components/tool-results/action-badges';

export function CreateRuleBody({
  action,
  editing,
  onOverrideChange,
}: {
  action: PreviewCardAction;
  editing: boolean;
  onOverrideChange: (key: string, value: string) => void;
}) {
  const t = useTranslations('chat.preview');
  const sourceText = textValue(action.input.sourceText);
  const name = textValue(action.input.displayName) || sourceText;

  if (editing) {
    return (
      <div className="grid gap-3 text-sm">
        <div className="grid gap-1.5">
          <label className="text-muted-foreground text-xs font-medium uppercase">
            {t('ruleName')}
          </label>
          <Input
            defaultValue={name}
            onChange={(event) => onOverrideChange('displayName', event.currentTarget.value)}
          />
        </div>
        <div className="grid gap-1.5">
          <label className="text-muted-foreground text-xs font-medium uppercase">{t('when')}</label>
          <Textarea
            className="min-h-20"
            defaultValue={sourceText}
            onChange={(event) => onOverrideChange('sourceText', event.currentTarget.value)}
          />
          <p className="text-muted-foreground text-xs">{t('ruleEditHint')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="grid gap-3 text-sm">
      <div>
        <p className="text-muted-foreground text-xs font-medium uppercase">{t('ruleName')}</p>
        <p className="font-semibold">{name}</p>
      </div>
      {sourceText && sourceText !== name && (
        <div>
          <p className="text-muted-foreground text-xs font-medium uppercase">{t('when')}</p>
          <p className="mt-1">{sourceText}</p>
        </div>
      )}
      <div>
        <p className="text-muted-foreground text-xs font-medium uppercase">{t('then')}</p>
        <div className="mt-1">
          <ActionBadges actionIntents={action.input.actionIntents} />
        </div>
      </div>
    </div>
  );
}
