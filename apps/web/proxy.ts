import { NextResponse, type NextRequest } from "next/server";

const PROTECTED = ["/onboarding", "/settings"];

export function proxy(req: NextRequest) {
    const needsAuth = PROTECTED.some((p) => req.nextUrl.pathname.startsWith(p));
    if (!needsAuth) return NextResponse.next();
    const session = req.cookies.get("ZEROMAIL_SESSION");
    if (!session) return NextResponse.redirect(new URL("/login", req.url));
    return NextResponse.next();
}

export const config = {
    matcher: ["/onboarding/:path*", "/settings/:path*"],
};
