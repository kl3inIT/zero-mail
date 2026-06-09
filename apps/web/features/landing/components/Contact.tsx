import { ContactForm } from './ContactForm';

export default function Contact() {
  return (
    <section className="zm-section bg-(--bg) py-24" id="contact">
      <div className="zm-container max-w-3xl">
        <div className="mb-12 text-center">
          <h2 className="mb-4 text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            Get in touch
          </h2>
          <p className="mx-auto max-w-xl text-[17px] leading-relaxed text-(--text-muted)">
            Have a question, found a bug, or want to suggest something? Send us a message — we read
            every one and reply within 24 hours on business days.
          </p>
        </div>

        <ContactForm />
      </div>
    </section>
  );
}
