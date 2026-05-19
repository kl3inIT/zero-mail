import { ShieldAlert } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';

export function VipBanner({
  checked,
  onCheckedChange,
}: {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
}) {
  const t = useTranslations('chat.vip');

  return (
    <div
      className="bg-amber-soft text-amber border-amber/20 flex items-start gap-3 rounded-md border p-3"
      role="alert"
    >
      <ShieldAlert className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1 space-y-2">
        <p className="text-sm font-semibold">{t('title')}</p>
        <Label className="flex items-center gap-2 text-xs font-medium">
          <Checkbox
            checked={checked}
            onCheckedChange={(value) => onCheckedChange(value === true)}
          />
          {t('acknowledge')}
        </Label>
      </div>
    </div>
  );
}
