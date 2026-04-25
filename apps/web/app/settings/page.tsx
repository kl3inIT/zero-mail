"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ConnectionHealthBadge } from "@/components/ConnectionHealthBadge";
import { DeleteAccountDialog } from "@/components/DeleteAccountDialog";
import { ReconnectPrompt } from "@/components/ReconnectPrompt";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { api, xsrfHeader } from "@/lib/api/client";

type MeData = { email?: string };
type StatusData = { connectionStatus: "CONNECTED" | "DISCONNECTED" | "NOT_CONNECTED" | "PENDING" };

export default function SettingsPage() {
    const qc = useQueryClient();
    const apiBase = process.env.NEXT_PUBLIC_API_BASE ?? "";
    const me = useQuery<MeData | undefined>({
        queryKey: ["me"],
        queryFn: async () => (await api.GET("/me", {})).data as MeData | undefined,
    });
    const status = useQuery<StatusData | undefined>({
        queryKey: ["status"],
        queryFn: async () => (await api.GET("/tenant/status", {})).data as StatusData | undefined,
    });
    const disconnect = useMutation({
        mutationFn: async () =>
            (
                await api.POST("/tenant/disconnect", {
                    headers: xsrfHeader(),
                })
            ).data,
        onSuccess: () => qc.invalidateQueries({ queryKey: ["status"] }),
    });
    const del = useMutation({
        mutationFn: async () =>
            (
                await api.DELETE("/me/account", {
                    headers: xsrfHeader(),
                })
            ).data,
        onSuccess: () => {
            window.location.href = "/login";
        },
    });

    const connStatus = status.data?.connectionStatus ?? "NOT_CONNECTED";
    const reconnect = () => {
        window.location.href = `${apiBase}/tenant/connect-gmail`;
    };

    return (
        <main className="max-w-4xl mx-auto p-6 space-y-6">
            <Card className="p-6">
                <h2 className="text-xl font-semibold">Account</h2>
                <p className="mt-2">{me.data?.email ?? "Signed in"}</p>
            </Card>
            <Card className="p-6">
                <h2 className="text-xl font-semibold">Gmail connection</h2>
                <div className="flex items-center gap-3 mt-3">
                    <ConnectionHealthBadge status={connStatus} />
                </div>
                {connStatus === "DISCONNECTED" && (
                    <div className="mt-3">
                        <ReconnectPrompt onReconnect={reconnect} />
                    </div>
                )}
                {connStatus === "NOT_CONNECTED" && (
                    <form method="post" action={`${apiBase}/tenant/connect-gmail`} className="mt-3">
                        <Button type="submit">Connect Gmail</Button>
                    </form>
                )}
            </Card>
            <Card className="p-6">
                <h2 className="text-xl font-semibold">Privacy &amp; safety</h2>
                <ul className="mt-3 list-disc pl-5 text-sm">
                    <li>No long-term storage of raw email bodies, prompts, completions, or embeddings.</li>
                    <li>No auto-send in v1 — drafts always require your review in Gmail.</li>
                    <li>You can revoke the Gmail grant any time from your Google account.</li>
                    <li>BYOK (your own API keys) is planned for later phases.</li>
                </ul>
            </Card>
            <Separator />
            <Card className="p-6 border-red-200">
                <h2 className="text-xl font-semibold">Danger zone</h2>
                <div className="flex gap-3 mt-4">
                    <Button variant="destructive" onClick={() => disconnect.mutate()}>
                        Disconnect Gmail
                    </Button>
                    <DeleteAccountDialog onConfirm={async () => {
                        await del.mutateAsync();
                    }} />
                </div>
            </Card>
        </main>
    );
}
