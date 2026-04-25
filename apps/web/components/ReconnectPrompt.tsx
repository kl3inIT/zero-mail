import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

export function ReconnectPrompt({ onReconnect }: { onReconnect: () => void }) {
    return (
        <Alert variant="default" className="border-amber-500 bg-amber-50">
            <p>Google access was revoked or expired. Reconnect Gmail to continue setup.</p>
            <Button onClick={onReconnect} className="mt-3">
                Reconnect Gmail
            </Button>
        </Alert>
    );
}
