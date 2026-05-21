import { getAdminApiUrl } from './api/admin-base-url';
import type { components } from './api/admin-schema';

export type AdminMe = components['schemas']['AdminMeResponse'];

export async function getCurrentAdmin(): Promise<AdminMe> {
  const response = await fetch(getAdminApiUrl('/api/admin/me'), {
    credentials: 'include',
  });
  if (response.status === 401) {
    throw new Error('Phiên quản trị đã hết hạn');
  }
  if (!response.ok) {
    throw new Error(`Truy vấn phiên quản trị thất bại: ${response.status}`);
  }
  return (await response.json()) as AdminMe;
}

function readXsrfCookie(): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

export async function logoutAdmin(): Promise<void> {
  const xsrfToken = readXsrfCookie();
  const response = await fetch(getAdminApiUrl('/api/admin/logout'), {
    method: 'POST',
    credentials: 'include',
    headers: xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken } : undefined,
  });
  if (!response.ok) {
    throw new Error(`Đăng xuất thất bại: ${response.status}`);
  }
}
