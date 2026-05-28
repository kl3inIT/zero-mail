import { SubscriptionPage } from '@/features/subscription/components/SubscriptionPage';

export default function SubscriptionRoute() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <SubscriptionPage />
      </div>
    </div>
  );
}
