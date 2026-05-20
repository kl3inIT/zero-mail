import { expect, test } from '@playwright/test';

const token = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

async function stubWebAuthn(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    Object.defineProperty(window, 'PublicKeyCredential', {
      value: function PublicKeyCredential() {},
      configurable: true,
    });
    Object.defineProperty(navigator, 'credentials', {
      value: {
        async create() {
          const bytes = new Uint8Array([1, 2, 3, 4]).buffer;
          return {
            id: 'Y3JlZGVudGlhbC1pZA',
            rawId: bytes,
            type: 'public-key',
            response: {
              attestationObject: bytes,
              clientDataJSON: bytes,
              getTransports() {
                return ['internal'];
              },
            },
            getClientExtensionResults() {
              return {};
            },
            toJSON() {
              return {
                id: 'Y3JlZGVudGlhbC1pZA',
                rawId: 'Y3JlZGVudGlhbC1pZA',
                response: {
                  attestationObject: 'YXR0ZXN0YXRpb24',
                  clientDataJSON: 'Y2xpZW50LWRhdGE',
                },
                type: 'public-key',
                clientExtensionResults: {},
              };
            },
          };
        },
        async get() {
          const bytes = new Uint8Array([5, 6, 7, 8]).buffer;
          return {
            id: 'Y3JlZGVudGlhbC1pZA',
            rawId: bytes,
            type: 'public-key',
            response: {
              authenticatorData: bytes,
              clientDataJSON: bytes,
              signature: bytes,
              userHandle: bytes,
            },
            getClientExtensionResults() {
              return {};
            },
            toJSON() {
              return {
                id: 'Y3JlZGVudGlhbC1pZA',
                rawId: 'Y3JlZGVudGlhbC1pZA',
                response: {
                  authenticatorData: 'YXV0aC1kYXRh',
                  clientDataJSON: 'Y2xpZW50LWRhdGE',
                  signature: 'c2lnbmF0dXJl',
                  userHandle: 'dXNlci1oYW5kbGU',
                },
                type: 'public-key',
                clientExtensionResults: {},
              };
            },
          };
        },
      },
      configurable: true,
    });
  });
}

test('enroll-and-login passkey flow reaches authenticated admin shell', async ({ page }) => {
  await stubWebAuthn(page);
  await page.route('**/api/admin/enrollment/session', async (route) => {
    await route.fulfill({ json: { expiresAt: '2026-05-20T00:10:00Z' } });
  });
  await page.route('**/webauthn/register/options', async (route) => {
    await route.fulfill({
      json: {
        challenge: 'Y2hhbGxlbmdl',
        rp: { name: 'Zero Mail Admin', id: 'localhost' },
        user: { id: 'dXNlci1oYW5kbGU', name: 'admin@example.com', displayName: 'admin@example.com' },
        pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
        timeout: 60000,
        attestation: 'none',
        authenticatorSelection: { userVerification: 'required' },
      },
    });
  });
  await page.route('**/webauthn/register', async (route) => {
    await route.fulfill({ json: { verified: true } });
  });
  await page.route('**/webauthn/authenticate/options', async (route) => {
    await route.fulfill({
      json: {
        challenge: 'Y2hhbGxlbmdl',
        rpId: 'localhost',
        allowCredentials: [{ type: 'public-key', id: 'Y3JlZGVudGlhbC1pZA' }],
        userVerification: 'required',
      },
    });
  });
  await page.route('**/login/webauthn', async (route) => {
    await route.fulfill({ json: { verified: true } });
  });
  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({ json: { adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev' } });
  });
  await page.route('**/api/admin/audit/events**', async (route) => {
    await route.fulfill({
      json: {
        rows: [
          {
            auditId: 'audit-1',
            chainIndex: 1,
            actorEmail: 'admin@example.com',
            action: 'ADMIN_LOGIN',
            createdAt: '2026-05-20T00:00:00Z',
          },
        ],
        hasNextPage: false,
        totalEstimate: 1,
      },
    });
  });

  await page.goto(`/enroll?token=${token}&email=admin@example.com`);
  await page.getByRole('button', { name: 'Register passkey' }).click();
  await expect(page.getByRole('heading', { name: 'Admin sign-in' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign in with passkey' }).click();

  await expect(page.getByText('ADMIN MODE')).toBeVisible();
  await expect(page.getByText('actions affect real tenants')).toBeVisible();
});
