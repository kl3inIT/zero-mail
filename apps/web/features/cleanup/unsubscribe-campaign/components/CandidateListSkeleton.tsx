import { Skeleton } from '@/components/ui/skeleton';

export function CandidateListSkeleton() {
  return (
    <div className="flex flex-col gap-2">
      {Array.from({ length: 8 }).map((_, rowIndex) => (
        <Skeleton key={rowIndex} className="h-12 w-full" />
      ))}
    </div>
  );
}
