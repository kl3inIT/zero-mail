import { expect, test } from "@playwright/test";

/**
 * Phase 1.1 Plan 07 Task 2 — Switcher persistence smoke (REQ-2).
 *
 * Threat reference (planning_context security_threat_model #4):
 *   "NEXT_LOCALE downgraded to session cookie" — mitigated by asserting the
 *   cookie's `expires` is roughly 1 year from now (>= now + 364d).
 *
 * Coverage scope (intentionally narrow per VALIDATION.md):
 *   - Test #1 (UNAUTHENTICATED, runs): visit /login, default vi, switch to en,
 *     assert <html lang="en"> AND NEXT_LOCALE cookie persisted with 1y expiry.
 *   - Test #2 (AUTHENTICATED, skipped): the PATCH /me/language flow needs an
 *     auth fixture that Phase 1.2 will provide. Plan 06 Task 3 step 10 is the
 *     compensating manual checkpoint and MUST be filled in before this plan
 *     can be considered fully approved.
 */

test("default Vietnamese, switch to English, NEXT_LOCALE cookie persists across context restart with ~1y maxAge", async ({
    browser,
}) => {
    const ctx1 = await browser.newContext();
    const page1 = await ctx1.newPage();
    await page1.goto("/login");

    // Default is Vietnamese (i18n/routing.ts defaultLocale = 'vi').
    await expect(page1.locator("html")).toHaveAttribute("lang", "vi");

    // Open the switcher (compact variant — top-right of /login card).
    // The aria-label is the localized "settings.language.label" — both vi
    // ("Ngôn ngữ") and en ("Language") match the regex below.
    await page1
        .getByRole("button", { name: /ngôn ngữ|language/i })
        .first()
        .click();

    // Pick "English". The menu items have role="menuitemradio" and
    // aria-label = the locale's own native name ("English" for en).
    await page1
        .getByRole("menuitemradio", { name: /english/i })
        .click();

    // After router.refresh() the RSC re-renders <html lang="en">.
    await expect(page1.locator("html")).toHaveAttribute("lang", "en");

    // The cookie must be NEXT_LOCALE=en with a far-future expiry (~1y, REQ-2).
    const cookies = await ctx1.cookies();
    const next = cookies.find((c) => c.name === "NEXT_LOCALE");
    expect(next, "NEXT_LOCALE cookie must be set after switcher click").toBeDefined();
    expect(next?.value).toBe("en");

    // expires is a unix-epoch number-of-seconds; -1 means session cookie (FAIL).
    // We tolerate ~1 day clock drift but require >= now + 364d.
    const nowSec = Math.floor(Date.now() / 1000);
    const minExpiry = nowSec + 60 * 60 * 24 * 364;
    expect(next?.expires ?? -1).toBeGreaterThanOrEqual(minExpiry);

    // Simulate "browser restart": new context inheriting the persisted cookie.
    // newContext() does NOT inherit cookies by default — that IS the realistic
    // browser-restart scenario. We re-issue the cookie programmatically (the
    // browser would do this from its on-disk cookie jar) and assert that the
    // page renders English on first paint.
    const ctx2 = await browser.newContext({
        storageState: { cookies: cookies, origins: [] },
    });
    const page2 = await ctx2.newPage();
    await page2.goto("/login");
    await expect(page2.locator("html")).toHaveAttribute("lang", "en");

    await ctx1.close();
    await ctx2.close();
});

test("authenticated PATCH /me/language smoke", async () => {
    test.skip(
        true,
        "Requires authenticated test fixture - Phase 1.2 will add the auth helper. " +
            "Cross-browser cross-session PATCH /me/language coverage is provided MANUALLY " +
            "by Plan 06 Task 3 step 10 (logged in 01.1-06-SUMMARY.md with date + reviewer initials).",
    );
});
