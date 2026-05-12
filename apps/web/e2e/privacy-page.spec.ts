import { expect, test, type Page } from '@playwright/test';

import {
  createChromeMockState,
  expectAppShellChrome,
  expectNoClaySkinClasses,
  expectNoHorizontalOverflow,
  installChromeApiMock,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
] as const;

for (const viewport of VIEWPORTS) {
  test(`authenticated privacy page renders in-shell policy points at ${viewport.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await openAuthenticatedPage(page, '/settings/privacy', 'en');

    await expectAppShellChrome(page, { sidebarVisible: viewport.name === 'desktop' });
    await expect(page.getByRole('heading', { name: 'In-product privacy' })).toBeVisible();
    await expect(page.getByText('What we never store', { exact: true })).toBeVisible();
    await expect(page.getByText('email bodies')).toBeVisible();
    await expect(page.getByText('AI prompts')).toBeVisible();
    await expect(page.getByText('AI replies')).toBeVisible();
    await expect(page.getByText('embeddings')).toBeVisible();
    await expect(page.getByText("What Zero Mail can and can't do", { exact: true })).toBeVisible();
    await expect(page.getByText('cannot send email on your behalf')).toBeVisible();
    await expect(page.getByText('Using your own AI key (BYOK)', { exact: true })).toBeVisible();
    await expect(page.getByText('Bring your own model key in Settings')).toBeVisible();

    const publicPrivacyLink = page.getByRole('link', { name: 'View the public privacy page' });
    await expect(publicPrivacyLink).toBeVisible();
    await expect(publicPrivacyLink).toHaveAttribute('href', '/privacy');
    await expectNoClaySkinClasses(page);
    await expectNoHorizontalOverflow(page);
  });
}

test('authenticated privacy page renders Vietnamese copy', async ({ page }) => {
  await openAuthenticatedPage(page, '/settings/privacy', 'vi');

  await expectAppShellChrome(page, { sidebarVisible: true });
  await expect(page.getByRole('heading', { name: 'Quyền riêng tư trong sản phẩm' })).toBeVisible();
  await expect(
    page.getByText('Những gì chúng tôi không bao giờ lưu', { exact: true }),
  ).toBeVisible();
  await expect(page.getByText('không lưu lâu dài nội dung email')).toBeVisible();
  await expect(page.getByText('Zero Mail không thể gửi email thay bạn')).toBeVisible();
  await expectNoClaySkinClasses(page);
});

test('privacy page is reachable from settings link', async ({ page }) => {
  await openAuthenticatedPage(page, '/settings', 'en');

  await expectAppShellChrome(page, { sidebarVisible: true });
  await expectNoClaySkinClasses(page);
  await page.getByRole('link', { name: 'Privacy & data handling' }).click();

  await expect(page).toHaveURL(/\/settings\/privacy$/);
  await expect(page.getByRole('heading', { name: 'In-product privacy' })).toBeVisible();
  await expectNoClaySkinClasses(page);
  await expectNoHorizontalOverflow(page);
});

async function openAuthenticatedPage(
  page: Page,
  path: '/settings' | '/settings/privacy',
  locale: 'en' | 'vi',
) {
  await page.context().addCookies([
    {
      name: 'ZEROMAIL_SESSION',
      value: 'playwright-session',
      domain: 'localhost',
      path: '/',
      httpOnly: true,
      sameSite: 'Lax',
      secure: false,
    },
    {
      name: 'NEXT_LOCALE',
      value: locale,
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
      secure: false,
    },
  ]);
  await installChromeApiMock(page, createChromeMockState());
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
}
