import { MoonIcon, SunIcon } from 'lucide-react';

import { toggleAdminTheme, useAdminTheme } from '@/lib/admin-theme';

import { Button } from './ui/button';

export function ThemeToggle({ className }: { className?: string }) {
  const theme = useAdminTheme();
  const isDark = theme === 'dark';
  return (
    <Button
      variant="ghost"
      size="icon"
      type="button"
      aria-label={isDark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'}
      title={isDark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'}
      onClick={() => toggleAdminTheme()}
      className={className}
    >
      {isDark ? <SunIcon className="size-4" /> : <MoonIcon className="size-4" />}
    </Button>
  );
}
