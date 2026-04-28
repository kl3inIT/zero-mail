import { Button } from '@/components/ui/button';
import { MoonIcon, SunIcon } from '@/features/landing/components/PrototypeIcons';

type Props = {
  currentTheme: 'light' | 'dark';
  label: string;
};

export function ThemeToggle({ currentTheme, label }: Props) {
  const next: 'light' | 'dark' = currentTheme === 'dark' ? 'light' : 'dark';

  return (
    <form action="/actions/theme" method="post">
      <input type="hidden" name="theme" value={next} />
      <Button
        type="submit"
        variant="outline"
        size="icon"
        aria-label={label}
        title={label}
        aria-pressed={currentTheme === 'dark'}
        className="zm-icon-btn min-h-11 min-w-11 sm:min-h-8 sm:min-w-8"
      >
        {currentTheme === 'dark' ? <SunIcon size={15} /> : <MoonIcon size={15} />}
      </Button>
    </form>
  );
}
