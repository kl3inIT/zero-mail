import { PlanList } from '@/features/billing/components/PlanList';

export default function SubscriptionRoute() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <div className="mx-auto max-w-6xl space-y-8">
          <header className="space-y-2 text-center">
            <h1 className="text-foreground text-3xl font-bold tracking-tight md:text-4xl">
              Chọn gói phù hợp
            </h1>
            <p className="text-muted-foreground mx-auto max-w-2xl text-sm md:text-base">
              Mỗi gói reset credit hàng tháng. Khi hết credit bạn không bị mất truy cập — chỉ tạm
              dừng các tác vụ tốn LLM cho tới chu kỳ kế tiếp.
            </p>
          </header>
          <PlanList />
        </div>
      </div>
    </div>
  );
}
