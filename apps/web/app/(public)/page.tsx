import Features from '@/features/landing/components/Features';
import Hero from '@/features/landing/components/Hero';
import HowItWorks from '@/features/landing/components/HowItWorks';
import TrustPillars from '@/features/landing/components/TrustPillars';

export default async function LandingPage() {
  return (
    <>
      <Hero />
      <HowItWorks />
      <Features />
      <TrustPillars />
    </>
  );
}
