import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import ZMLogoMark from '@/features/landing/components/ZMLogoMark';

export default async function Footer() {
  const t = await getTranslations();

  return (
    <footer className="zm-footer">
      <div className="zm-container">
        <div className="zm-footer-row">
          <div className="max-w-72">
            <Link href="/" className="zm-brand mb-4">
              <span className="zm-brand-mark">
                <ZMLogoMark size={48} />
              </span>
              <span className="zm-brand-wordmark">
                <span>zero</span>
                <span className="light">mail</span>
              </span>
            </Link>
            <p className="text-sm leading-relaxed">{t('footer.tagline')}</p>
          </div>
          <div className="zm-footer-cols">
            <div className="zm-footer-col">
              <h2>{t('footer.product')}</h2>
              <Link href="/#features">{t('nav.features')}</Link>
              <Link href="/#testimonials">{t('nav.testimonials')}</Link>
              <Link href="/#faq">{t('nav.faq')}</Link>
            </div>
            <div className="zm-footer-col">
              <h2>{t('footer.legal')}</h2>
              <Link href="/privacy">{t('footer.privacy')}</Link>
              <Link href="/terms">{t('footer.terms')}</Link>
            </div>
          </div>
        </div>
        <div className="zm-footer-bottom">
          <span>{t('footer.copyright')}</span>
          <span className="inline-flex items-center gap-3">
            <span className="inline-flex items-center gap-1.5">
              <span className="size-1.5 rounded-full bg-(--green)" />
              {t('footer.status')}
            </span>
            <span>{t('footer.version')}</span>
          </span>
        </div>
      </div>
    </footer>
  );
}
