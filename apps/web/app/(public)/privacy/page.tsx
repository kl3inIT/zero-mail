import type { Metadata } from 'next';

import { LegalDocPage, generateLegalDocMetadata } from '@/lib/docs/legal-page';

export async function generateMetadata(): Promise<Metadata> {
  return generateLegalDocMetadata('privacy');
}

export default async function PrivacyPage() {
  return <LegalDocPage slug="privacy" />;
}
