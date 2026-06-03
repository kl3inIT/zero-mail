import { redirect } from 'next/navigation';

export default function SuppressionRoutePage() {
  redirect('/cleanup/bulk-unsubscribe');
}
