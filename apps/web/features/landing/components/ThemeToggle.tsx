'use client';

import { useTranslations } from 'next-intl';

import { buttonVariants } from '@/components/ui/button';
import { setTheme } from '@/features/landing/lib/setTheme';
import { cn } from '@/lib/utils';

type Props = { currentTheme: 'light' | 'dark' };

export function ThemeToggle({ currentTheme }: Props) {
  const t = useTranslations();
  const next: 'light' | 'dark' = currentTheme === 'dark' ? 'light' : 'dark';
  const ariaLabel = currentTheme === 'dark' ? t('nav.themeToLight') : t('nav.themeToDark');

  return (
    <form action={setTheme}>
      <input type="hidden" name="theme" value={next} />
      <button
        type="submit"
        aria-label={ariaLabel}
        aria-pressed={currentTheme === 'dark'}
        className={cn(
          buttonVariants({ variant: 'ghost', size: 'icon' }),
          'min-h-[44px] min-w-[44px]',
        )}
      >
        {currentTheme === 'dark' ? <SunIcon /> : <MoonIcon />}
      </button>
    </form>
  );
}

function SunIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
    </svg>
  );
}

function MoonIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4"
      aria-hidden="true"
    >
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  );
}
