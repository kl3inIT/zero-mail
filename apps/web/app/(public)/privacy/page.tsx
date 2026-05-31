import type { Metadata } from 'next';

import { LegalDocPage } from '@/lib/docs/legal-page';
import { generateLegalDocMetadata } from '@/lib/docs/legal-page-data';

export async function generateMetadata(): Promise<Metadata> {
  return generateLegalDocMetadata('privacy');
}

export default async function PrivacyPage() {
  return <LegalDocPage slug="privacy" />;
}
