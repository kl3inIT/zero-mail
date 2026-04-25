import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";

const SPEC_URL = process.env.API_SPEC_URL ?? "http://localhost:8080/v3/api-docs";
const OUT = "lib/api/schema.d.ts";

async function main(): Promise<void> {
    const res = await fetch(SPEC_URL);
    if (!res.ok) throw new Error(`spec fetch ${res.status}`);
    const spec = await res.text();
    mkdirSync("openapi", { recursive: true });
    writeFileSync("openapi/spec.json", spec);
    execFileSync("pnpm", ["exec", "openapi-typescript", "openapi/spec.json", "-o", OUT], {
        stdio: "inherit",
        shell: process.platform === "win32",
    });
}

main().catch((e) => {
    console.error(e);
    process.exit(1);
});
