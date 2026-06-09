import { ContactForm } from './ContactForm';

export default function Contact() {
  return (
    <section className="zm-section bg-(--bg) py-24" id="contact">
      <div className="zm-container max-w-xl">
        <div className="mb-10 text-center">
          <h2 className="mb-3 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            Get in touch
          </h2>
          <p className="text-[17px] leading-relaxed text-(--text-muted)">
            Bug report, feature idea, or just a question? Drop us a message.
          </p>
        </div>

        <ContactForm />
      </div>
    </section>
  );
}
