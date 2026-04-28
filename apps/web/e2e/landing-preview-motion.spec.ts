import { expect, test } from '@playwright/test';

test('hero inbox preview cycles the active AI suggestion', async ({ page }) => {
  await page.goto('/', { waitUntil: 'domcontentloaded' });

  const preview = page.locator('.zm-gm');
  await expect(preview).toBeVisible();
  await expect(preview).toHaveAttribute('data-motion', 'triage-cycle');

  await expect
    .poll(
      () =>
        preview.evaluate((node) =>
          node
            .getAnimations({ subtree: true })
            .some((animation) => animation.playState === 'running'),
        ),
      { timeout: 3_000, intervals: [250] },
    )
    .toBe(true);
});
