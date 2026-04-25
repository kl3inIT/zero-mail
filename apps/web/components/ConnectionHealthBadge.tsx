import { Badge } from "@/components/ui/badge";

type Status = "CONNECTED" | "DISCONNECTED" | "NOT_CONNECTED" | "PENDING";

const copy: Record<Status, { label: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
    CONNECTED:     { label: "Connected",     variant: "default" },
    DISCONNECTED:  { label: "Disconnected",  variant: "destructive" },
    NOT_CONNECTED: { label: "Not connected", variant: "secondary" },
    PENDING:       { label: "Connecting…",   variant: "outline" },
};

export function ConnectionHealthBadge({ status }: { status: Status }) {
    const { label, variant } = copy[status];
    return <Badge variant={variant} aria-live="polite">{label}</Badge>;
}
