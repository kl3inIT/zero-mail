type Locale = 'en' | 'vi';

type Props = {
  currentLocale: Locale;
};

export function SegmentedLanguageToggle({ currentLocale }: Props) {
  return (
    <form
      action="/actions/locale"
      method="post"
      className="zm-segmented-lang"
      role="group"
      aria-label="Language"
    >
      <button
        type="submit"
        name="locale"
        value="en"
        aria-label="English"
        aria-pressed={currentLocale === 'en'}
      >
        EN
      </button>
      <span className="zm-lang-sep" aria-hidden="true" />
      <button
        type="submit"
        name="locale"
        value="vi"
        aria-label="Tiếng Việt"
        aria-pressed={currentLocale === 'vi'}
      >
        VI
      </button>
    </form>
  );
}
