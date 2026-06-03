import { redirect } from 'next/navigation';

export default function CleanupIndexPage() {
  redirect('/cleanup/bulk-unsubscribe');
}
