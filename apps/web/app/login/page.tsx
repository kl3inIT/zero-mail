import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export default function LoginPage() {
    const apiBase = process.env.NEXT_PUBLIC_API_BASE ?? "";
    return (
        <main className="min-h-dvh flex items-center justify-center p-6">
            <Card className="max-w-md w-full p-8">
                <h1 className="text-3xl font-semibold">Reach inbox zero without giving up control.</h1>
                <p className="mt-4 text-base">
                    Zero Mail connects to Gmail so you can set safe, reviewable automation rules. Phase 1
                    only stores your account, connection, and onboarding choices.
                </p>
                <ul className="mt-4 text-sm list-disc pl-5 text-stone-700">
                    <li>No auto-send</li>
                    <li>No long-term email body storage</li>
                    <li>You can revoke access anytime</li>
                </ul>
                <a href={`${apiBase}/oauth2/authorization/google`}>
                    <Button className="mt-6 w-full">Sign in with Google</Button>
                </a>
            </Card>
        </main>
    );
}
