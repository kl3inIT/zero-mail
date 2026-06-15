import { getTranslations } from 'next-intl/server';

import { ContactForm } from './ContactForm';

export default async function Contact() {
  const t = await getTranslations('landingContact');

  return (
    <section className="zm-section bg-(--bg) py-12" id="contact">
      <div className="zm-container max-w-xl">
        <div className="mb-10 text-center">
          <h2 className="mb-3 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('heading')}
          </h2>
          <p className="text-[17px] leading-relaxed text-(--text-muted)">{t('subheading')}</p>
        </div>

        <ContactForm
          labels={{
            emailLabel: t('emailLabel'),
            emailPlaceholder: t('emailPlaceholder'),
            messageLabel: t('messageLabel'),
            messagePlaceholder: t('messagePlaceholder'),
            submit: t('submit'),
            submitting: t('submitting'),
            trust: t('trust'),
            successHeading: t('successHeading'),
            successBody: t('successBody'),
            successReset: t('successReset'),
            errorMessage: t('errorMessage'),
          }}
        />
      </div>
    </section>
  );
}
