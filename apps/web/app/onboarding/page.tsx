"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { TemplateCard } from "@/components/TemplateCard";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { api, xsrfHeader } from "@/lib/api/client";

type TemplateKey = "archive-receipts" | "label-newsletters" | "pin-calendar";

const templates: { key: TemplateKey; title: string; desc: string }[] = [
    {
        key: "archive-receipts",
        title: "Archive receipts automatically",
        desc: "Start with a rule idea for receipts from services like Stripe, stores, and vendors.",
    },
    {
        key: "label-newsletters",
        title: "Label newsletters as Newsletters and skip inbox",
        desc: "Keep reading material grouped without letting it interrupt your inbox.",
    },
    {
        key: "pin-calendar",
        title: "Keep calendar invites and meeting notes on top",
        desc: "Prioritize scheduling and meeting context while later phases add real rule execution.",
    },
];

type MeData = { onboardingStep: string };

export default function OnboardingPage() {
    const qc = useQueryClient();
    const me = useQuery<MeData | undefined>({
        queryKey: ["me"],
        queryFn: async () => (await api.GET("/me", {})).data as MeData | undefined,
    });
    const selectMut = useMutation({
        mutationFn: async (templateKey: TemplateKey) =>
            (
                await api.POST("/onboarding/select-template", {
                    body: { templateKey },
                    headers: xsrfHeader(),
                })
            ).data,
        onSuccess: () => qc.invalidateQueries({ queryKey: ["me"] }),
    });
    const completeMut = useMutation({
        mutationFn: async () =>
            (
                await api.POST("/onboarding/complete", {
                    headers: xsrfHeader(),
                })
            ).data,
        onSuccess: () => qc.invalidateQueries({ queryKey: ["me"] }),
    });
    const [selected, setSelected] = useState<TemplateKey | null>(null);

    if (!me.data) return <p className="p-6">Loading…</p>;

    const step = me.data.onboardingStep;
    const apiBase = process.env.NEXT_PUBLIC_API_BASE ?? "";

    return (
        <main className="max-w-3xl mx-auto p-6">
            <p className="text-sm text-stone-600">Step {step === "SIGNED_IN" ? "1" : "2"} of 2</p>
            {step === "SIGNED_IN" && (
                <Card className="p-6 mt-4">
                    <h2 className="text-xl font-semibold">Connect Gmail</h2>
                    <p className="mt-2">
                        We need Gmail access to later label, archive, and draft replies. You can
                        revoke any time.
                    </p>
                    <form method="post" action={`${apiBase}/tenant/connect-gmail`} className="mt-4">
                        <Button type="submit">Connect Gmail</Button>
                    </form>
                </Card>
            )}
            {step === "GMAIL_CONNECTED" && (
                <div className="grid gap-4 mt-4">
                    {templates.map((t) => (
                        <TemplateCard
                            key={t.key}
                            templateKey={t.key}
                            title={t.title}
                            description={t.desc}
                            selected={selected === t.key}
                            onSelect={() => setSelected(t.key)}
                        />
                    ))}
                    <Button
                        disabled={!selected || selectMut.isPending}
                        onClick={() => selected && selectMut.mutate(selected)}
                    >
                        {selectMut.isPending ? "Saving starter template…" : "Save starter template"}
                    </Button>
                    <p className="text-sm text-stone-600">
                        We&apos;ll save this as your starter preference for the rules phase.
                    </p>
                </div>
            )}
            {step === "TEMPLATE_SELECTED" && (
                <Card className="p-6 mt-4">
                    <h2 className="text-xl font-semibold">All set</h2>
                    <Button onClick={() => completeMut.mutate()} className="mt-4">
                        Continue to settings
                    </Button>
                </Card>
            )}
            {step === "COMPLETE" && <p>Redirecting…</p>}
        </main>
    );
}
