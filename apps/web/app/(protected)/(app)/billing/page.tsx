import { BalanceCard } from '@/features/billing/components/BalanceCard';
import { LedgerHistory } from '@/features/billing/components/LedgerHistory';

export default function BillingPage() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <div className="grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
          <BalanceCard />
          <LedgerHistory />
        </div>
      </div>
    </div>
  );
}
