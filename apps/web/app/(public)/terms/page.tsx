import type { Metadata } from 'next';

import { LegalDocPage, generateLegalDocMetadata } from '@/lib/docs/legal-page';

export async function generateMetadata(): Promise<Metadata> {
  return generateLegalDocMetadata('terms');
}

export default async function TermsPage() {
  return <LegalDocPage slug="terms" />;
}
