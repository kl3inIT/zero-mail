import { LoadingState } from '@/components/states/LoadingState';

export default function DashboardLoading() {
  return (
    <div className="mx-auto w-full max-w-6xl p-4 md:p-6">
      <LoadingState variant="cards" count={3} />
    </div>
  );
}
