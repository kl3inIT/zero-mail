import { LoadingState } from '@/components/states/LoadingState';

export default function NeedsReplyLoading() {
  return (
    <div className="mx-auto w-full max-w-6xl p-4 md:p-6">
      <LoadingState variant="rows" count={5} />
    </div>
  );
}
