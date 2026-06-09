import Features from '@/features/landing/components/Features';
import Hero from '@/features/landing/components/Hero';
import Testimonials from '@/features/landing/components/Testimonials';
import FAQ from '@/features/landing/components/FAQ';
import Contact from '@/features/landing/components/Contact';

export default function LandingPage() {
  return (
    <>
      <Hero />
      <Features />
      <Testimonials />
      <FAQ />
      <Contact />
    </>
  );
}
