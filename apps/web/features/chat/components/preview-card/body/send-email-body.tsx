import { useTranslations } from 'next-intl';

import { Textarea } from '@/components/ui/textarea';
import { RecipientField } from '@/features/chat/components/preview-card/body/recipient-field';
import { CopyButton } from '@/features/chat/components/preview-card/copy-button';
import {
  type PreviewCardAction,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-1">
      <dt className="text-muted-foreground text-xs font-medium uppercase">{label}</dt>
      <dd className="text-foreground text-sm leading-relaxed">{children}</dd>
    </div>
  );
}

export function SendEmailBody({
  action,
  draftBody,
  editing,
  onOverrideChange,
}: {
  action: PreviewCardAction;
  draftBody: string;
  editing: boolean;
  onOverrideChange: (key: string, value: string) => void;
}) {
  const t = useTranslations('chat.preview');

  return (
    <dl className="grid gap-3">
      <Field label={t('to')}>
        {editing ? (
          <RecipientField
            defaultValue={textValue(action.input.to)}
            onChange={(value) => onOverrideChange('to', value)}
          />
        ) : (
          <span className="text-[15px] font-semibold">{textValue(action.input.to)}</span>
        )}
      </Field>
      {textValue(action.input.cc) && <Field label={t('cc')}>{textValue(action.input.cc)}</Field>}
      {textValue(action.input.bcc) && <Field label={t('bcc')}>{textValue(action.input.bcc)}</Field>}
      <Field label={t('subject')}>
        {editing ? (
          <Textarea
            className="min-h-10"
            defaultValue={textValue(action.input.subject)}
            onChange={(event) => onOverrideChange('subject', event.currentTarget.value)}
          />
        ) : (
          textValue(action.input.subject)
        )}
      </Field>
      <Field label={t('body')}>
        {editing ? (
          <Textarea
            className="min-h-32"
            defaultValue={draftBody}
            onChange={(event) => onOverrideChange('body', event.currentTarget.value)}
          />
        ) : (
          <div className="space-y-1">
            <span className="whitespace-pre-wrap">{draftBody}</span>
            {draftBody && <CopyButton text={draftBody} className="mt-1" />}
          </div>
        )}
      </Field>
    </dl>
  );
}
