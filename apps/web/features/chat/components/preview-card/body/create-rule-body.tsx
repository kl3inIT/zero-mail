import { useTranslations } from 'next-intl';

import {
  type PreviewCardAction,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';

function readableJson(value: unknown): string {
  if (!value) return '';
  if (typeof value === 'string') return value;
  return JSON.stringify(value, null, 2);
}

export function CreateRuleBody({ action }: { action: PreviewCardAction }) {
  const t = useTranslations('chat.preview');

  return (
    <div className="grid gap-3 text-sm">
      <div>
        <p className="text-muted-foreground text-xs font-medium uppercase">{t('ruleName')}</p>
        <p className="font-semibold">
          {textValue(action.input.displayName) || textValue(action.input.sourceText)}
        </p>
      </div>
      <div>
        <p className="text-muted-foreground text-xs font-medium uppercase">{t('when')}</p>
        <pre className="bg-muted mt-1 overflow-auto rounded-md p-2 text-xs whitespace-pre-wrap">
          {readableJson(action.input.matcherAst ?? action.input.when ?? action.input.sourceText)}
        </pre>
      </div>
      <div>
        <p className="text-muted-foreground text-xs font-medium uppercase">{t('then')}</p>
        <pre className="bg-muted mt-1 overflow-auto rounded-md p-2 text-xs whitespace-pre-wrap">
          {readableJson(action.input.actionIntents ?? action.input.then)}
        </pre>
      </div>
    </div>
  );
}
