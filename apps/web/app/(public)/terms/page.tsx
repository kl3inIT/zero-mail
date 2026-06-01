import type { Metadata } from 'next';

import { LegalDocPage } from '@/lib/docs/legal-page';
import { generateLegalDocMetadata } from '@/lib/docs/legal-page-data';

export async function generateMetadata(): Promise<Metadata> {
  return generateLegalDocMetadata('terms');
}

export default async function TermsPage() {
  return <LegalDocPage slug="terms" />;
}
