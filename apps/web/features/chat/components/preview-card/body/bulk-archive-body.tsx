import { useTranslations } from 'next-intl';

import {
  type PreviewCardAction,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';

export function BulkArchiveBody({ action }: { action: PreviewCardAction }) {
  const t = useTranslations('chat.preview');
  const threadIds = Array.isArray(action.input.threadIds) ? action.input.threadIds : [];
  const subjects = Array.isArray(action.input.sampleSubjects) ? action.input.sampleSubjects : [];
  const count = threadIds.length || Number(action.input.threadCount ?? 0);
  const shownSubjects = subjects.slice(0, 5).map(textValue).filter(Boolean);

  return (
    <div className="grid gap-2 text-sm">
      <p className="font-semibold">{t('threadCount', { count })}</p>
      {shownSubjects.length > 0 && (
        <ul className="text-muted-foreground list-disc space-y-1 pl-5">
          {shownSubjects.map((subject) => (
            <li key={subject}>{subject}</li>
          ))}
          {count > shownSubjects.length && (
            <li>{t('andMore', { count: count - shownSubjects.length })}</li>
          )}
        </ul>
      )}
    </div>
  );
}
