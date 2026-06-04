import type { Route } from 'next';
import { redirect } from 'next/navigation';

export default function CleanupIndexPage() {
  redirect('/cleanup/bulk-unsubscribe' as Route);
}
