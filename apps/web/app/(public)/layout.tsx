import Footer from '@/features/landing/components/Footer';
import TopBar from '@/features/landing/components/TopBar';

/**
 * Public route group layout (Phase 1.3 Plan 05 — D-C2, D-C4).
 *
 * Owns the public chrome around a single <main>. The root layout owns
 * <html>/<body>; this layout MUST NOT redefine them.
 */
export default async function PublicLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="zm-proto flex min-h-full flex-col">
      <TopBar />
      <main className="flex-1">{children}</main>
      <Footer />
    </div>
  );
}
