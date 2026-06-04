import { useTranslations } from 'next-intl';

import {
  type PreviewCardAction,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';
import { ActionBadges } from '@/features/chat/components/tool-results/action-badges';

export function CreateRuleBody({ action }: { action: PreviewCardAction }) {
  const t = useTranslations('chat.preview');
  const sourceText = textValue(action.input.sourceText);
  const name = textValue(action.input.displayName) || sourceText;

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
