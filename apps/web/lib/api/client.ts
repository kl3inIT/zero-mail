import createClient from "openapi-fetch";

import type { paths } from "./schema";

const typedApi = createClient<paths>({
    baseUrl: process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080",
    credentials: "include",
});

// Loose-typed surface for the placeholder schema. Once `pnpm generate:api`
// produces real `paths`, route components automatically get strong typing
// via `typedApi`. The cast below keeps the call sites in routes ergonomic
// while the placeholder is in effect.
type LooseClient = {
    GET:    (path: string, init?: Record<string, unknown>) => Promise<{ data: unknown; error?: unknown; response: Response }>;
    POST:   (path: string, init?: Record<string, unknown>) => Promise<{ data: unknown; error?: unknown; response: Response }>;
    PUT:    (path: string, init?: Record<string, unknown>) => Promise<{ data: unknown; error?: unknown; response: Response }>;
    DELETE: (path: string, init?: Record<string, unknown>) => Promise<{ data: unknown; error?: unknown; response: Response }>;
    PATCH:  (path: string, init?: Record<string, unknown>) => Promise<{ data: unknown; error?: unknown; response: Response }>;
};

export const api = typedApi as unknown as LooseClient;

export function xsrfHeader(): HeadersInit {
    if (typeof document === "undefined") return {};
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? { "X-XSRF-TOKEN": decodeURIComponent(match[1]) } : {};
}
