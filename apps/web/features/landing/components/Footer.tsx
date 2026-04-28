import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

export default async function Footer() {
  const t = await getTranslations();

  return (
    <footer className="border-border bg-background border-t">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-6 text-sm">
        <span className="text-muted-foreground">{t('footer.copyright')}</span>
        <div className="flex items-center gap-4">
          <Link
            href="/privacy"
            className="text-muted-foreground hover:text-foreground transition-colors"
          >
            {t('footer.privacy')}
          </Link>
          <Link
            href="/terms"
            className="text-muted-foreground hover:text-foreground transition-colors"
          >
            {t('footer.terms')}
          </Link>
        </div>
      </div>
    </footer>
  );
}
