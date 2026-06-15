import { FeatureBulkUnsubscribe } from '@/features/landing/components/FeatureBulkUnsubscribe';
import { FeatureChannels } from '@/features/landing/components/FeatureChannels';
import { FeatureDesignedAroundYou } from '@/features/landing/components/FeatureDesignedAroundYou';
import { FeatureDrafts } from '@/features/landing/components/FeatureDrafts';
import { FeatureGetStarted } from '@/features/landing/components/FeatureGetStarted';
import { FeatureOrganized } from '@/features/landing/components/FeatureOrganized';

export default function Features() {
  return (
    <div className="flex flex-col gap-24 bg-(--bg) py-24" id="features">
      {/* 1. Automatically Organized */}
      <FeatureOrganized />

      {/* 2. Pre-written drafts */}
      <FeatureDrafts />

      {/* 3. Your inbox, wherever you work */}
      <FeatureChannels />

      {/* 4. Get started in minutes */}
      <FeatureGetStarted />

      {/* 5. Bulk unsubscribe */}
      <FeatureBulkUnsubscribe />

      {/* 6. Designed around you */}
      <FeatureDesignedAroundYou />
    </div>
  );
}
