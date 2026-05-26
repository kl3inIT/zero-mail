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
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <CampaignStatusPage jobId={jobId} />
      </div>
    </div>
  );
}
