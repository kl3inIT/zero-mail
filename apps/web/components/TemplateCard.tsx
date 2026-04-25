"use client";

import { Card } from "@/components/ui/card";

type Props = {
    templateKey: "archive-receipts" | "label-newsletters" | "pin-calendar";
    title: string;
    description: string;
    selected: boolean;
    onSelect: () => void;
};

export function TemplateCard({ title, description, selected, onSelect }: Props) {
    return (
        <button
            type="button"
            onClick={onSelect}
            aria-pressed={selected}
            className={`text-left w-full ${selected ? "ring-2 ring-blue-600 rounded-xl" : ""}`}
        >
            <Card className="p-6">
                <h3 className="text-lg font-semibold">{title}</h3>
                <p className="text-sm text-stone-600 mt-2">{description}</p>
            </Card>
        </button>
    );
}
