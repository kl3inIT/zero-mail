import { UserPlus } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';

export function OutsideSourceThreadBadge() {
  const t = useTranslations('chat.preview');

  return (
    <Badge className="bg-blue-soft text-blue gap-1 border-0 px-2 py-0.5 text-xs font-medium">
      <UserPlus className="size-3" />
      {t('outsideSourceThread')}
    </Badge>
  );
}
