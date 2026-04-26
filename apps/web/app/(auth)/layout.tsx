/**
 * Auth route group layout (Phase 1.3 Plan 05 — REVIEWS Revision 2 Codex HIGH #4).
 *
 * MINIMAL passthrough: the existing login page already owns its inline <main>
 * + compact <LanguageSwitcher> in the card header. Adding chrome here would
 * duplicate that and produce nested <main> elements (acceptance criterion).
 *
 * The root layout owns <html>/<body>; this layout MUST NOT redefine them.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
