"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";

const CONFIRM_PHRASE = "delete my data";

export function DeleteAccountDialog({ onConfirm }: { onConfirm: () => Promise<void> }) {
    const [v, setV] = useState("");
    const [busy, setBusy] = useState(false);

    return (
        <Dialog>
            <DialogTrigger
                render={(props) => (
                    <Button {...props} variant="destructive">
                        Delete account and data
                    </Button>
                )}
            />

            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Delete your account and data?</DialogTitle>
                </DialogHeader>
                <p>
                    This removes your tenant, Gmail connection, onboarding selections, sessions, and
                    encrypted tokens. This cannot be undone.
                </p>
                <Input
                    value={v}
                    onChange={(e) => setV(e.target.value)}
                    placeholder={CONFIRM_PHRASE}
                    aria-label="Type delete my data to confirm"
                />
                <Button
                    variant="destructive"
                    disabled={v !== CONFIRM_PHRASE || busy}
                    onClick={async () => {
                        setBusy(true);
                        try {
                            await onConfirm();
                        } finally {
                            setBusy(false);
                        }
                    }}
                >
                    {busy ? "Deleting account and data…" : "Delete account and data"}
                </Button>
            </DialogContent>
        </Dialog>
    );
}
