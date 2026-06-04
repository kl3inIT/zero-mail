import { ExternalLink } from 'lucide-react';

export function OpenInGmailLink({
  href,
  label = 'Mở trong Gmail',
}: {
  href: string;
  label?: string;
}) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="text-primary inline-flex items-center gap-1 text-xs hover:underline"
    >
      <ExternalLink className="size-3" /> {label}
    </a>
  );
}
