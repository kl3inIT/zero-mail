"use client";

import { useTranslations } from "next-intl";

import { Badge } from "@/components/ui/badge";

type Status = "CONNECTED" | "DISCONNECTED" | "NOT_CONNECTED" | "PENDING";

const statusKey: Record<Status, { key: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
    CONNECTED:     { key: "connectionHealth.connected",    variant: "default" },
    DISCONNECTED:  { key: "connectionHealth.disconnected", variant: "destructive" },
    NOT_CONNECTED: { key: "connectionHealth.notConnected", variant: "secondary" },
    PENDING:       { key: "common.loading",                variant: "outline" },
};

export function ConnectionHealthBadge({ status }: { status: Status }) {
    const t = useTranslations();
    const { key, variant } = statusKey[status];
    return (
        <Badge variant={variant} aria-live="polite">
            {t(key as never)}
        </Badge>
    );
}
