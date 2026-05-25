import { notFound } from 'next/navigation';

import { CampaignStatusPage } from '@/features/cleanup/unsubscribe-campaign/components/CampaignStatusPage';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function CampaignStatusRoutePage({
  params,
}: {
  params: Promise<{ jobId: string }>;
}) {
  const { jobId } = await params;
  if (!UUID_PATTERN.test(jobId)) notFound();

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-5 p-4 md:p-6">
      <CampaignStatusPage jobId={jobId} />
    </div>
  );
}
